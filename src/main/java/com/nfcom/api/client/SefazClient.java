package com.nfcom.api.client;

import com.nfcom.api.service.CertLoader;
import com.nfcom.api.service.RateLimitBackoff;
import com.nfcom.api.shared.error.ErrorDetail;
import com.nfcom.api.shared.error.NfcomException;
import com.nfcom.api.xml.NfcomXmlParser;
import com.nfcom.api.xml.SoapMessageBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Core HTTP client for SEFAZ NFCom web services.
 * <p>
 * Sends SOAP 1.2 messages over HTTP with mTLS (via {@link CertLoader})
 * to all 5 SEFAZ NFCom services. Handles transport errors, HTTP error
 * status mapping, and rate-limit (cStat 678) detection with retry via
 * {@link RateLimitBackoff}.
 * <p>
 * Uses the JDK built-in {@link java.net.http.HttpClient} (no third-party
 * HTTP libraries).
 */
@ApplicationScoped
public class SefazClient {

    private static final Map<String, String> SERVICE_NAMESPACES = Map.of(
            "NFComRecepcao", "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao",
            "NFComConsulta", "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComConsulta",
            "NFComStatusServico", "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComStatusServico",
            "NFComRecepcaoEvento", "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcaoEvento",
            "ConsultaCadastro", "http://www.portalfiscal.inf.br/NFCom/wsdl/ConsultaCadastro"
    );

    private HttpClient httpClient;

    @Inject
    private CertLoader certLoader;

    @Inject
    private RateLimitBackoff rateLimitBackoff;

    @Inject
    private SoapMessageBuilder soapMessageBuilder;

    @Inject
    private NfcomXmlParser xmlParser;
    private final String baseUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    /**
     * Default constructor for CDI. Reads configuration from system properties
     * or environment variables.
     */
    public SefazClient() {
        this.baseUrl = readConfig("nfcom.sefaz.url",
                "https://dfe-portal.svrs.rs.gov.br/NFCom");
        this.connectTimeoutMs = parseIntConfig("nfcom.sefaz.connect-timeout-ms", 30000);
        this.readTimeoutMs = parseIntConfig("nfcom.sefaz.read-timeout-ms", 120000);
    }

    /**
     * Constructor for testing — allows direct injection of all dependencies.
     */
    SefazClient(CertLoader certLoader, RateLimitBackoff rateLimitBackoff,
                SoapMessageBuilder soapMessageBuilder, NfcomXmlParser xmlParser,
                String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.certLoader = certLoader;
        this.rateLimitBackoff = rateLimitBackoff;
        this.soapMessageBuilder = soapMessageBuilder;
        this.xmlParser = xmlParser;
        this.baseUrl = baseUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Initializes the {@link HttpClient} after dependency injection.
     * <p>
     * Configures:
     * <ul>
     *   <li>mTLS via {@link CertLoader#createSslContext()} (if available)</li>
     *   <li>Follow redirects: NEVER</li>
     *   <li>Connect timeout: configurable, default 30s</li>
     *   <li>Executor: virtual threads (Java 21+)</li>
     * </ul>
     */
    @PostConstruct
    void init() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs));

        if (certLoader != null) {
            SSLContext sslContext = certLoader.createSslContext();
            if (sslContext != null) {
                builder.sslContext(sslContext);
            }
        }

        // Use virtual thread executor (Java 21+)
        builder.executor(Executors.newVirtualThreadPerTaskExecutor());

        this.httpClient = builder.build();
    }

    /**
     * Sends a SOAP request to SEFAZ and returns the response body.
     * <p>
     * The request is sent with retry via {@link RateLimitBackoff}.
     * Rate-limit responses (cStat 678) are detected and trigger a retry.
     *
     * @param serviceName one of the 5 SEFAZ service names
     * @param soapRequest the full SOAP envelope XML string
     * @return the HTTP response body (full SOAP envelope XML)
     * @throws NfcomException with appropriate error code on failure
     */
    public String send(String serviceName, String soapRequest) {
        URI endpoint = resolveEndpoint(serviceName);

        return rateLimitBackoff.executeWithRetry(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(endpoint)
                        .header("Content-Type", "application/soap+xml;charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                soapRequest, StandardCharsets.UTF_8))
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                return handleResponse(response);

            } catch (HttpTimeoutException e) {
                throw new NfcomException("TIMEOUT", 504,
                        "SEFAZ request timed out: " + e.getMessage(),
                        List.of(new ErrorDetail(null,
                                "Connection or read timeout")));
            } catch (IOException e) {
                throw new NfcomException("CONNECTION_ERROR", 502,
                        "Connection to SEFAZ failed: " + e.getMessage(),
                        List.of(new ErrorDetail(null,
                                "I/O error during SEFAZ request")));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NfcomException("INTERRUPTED", 503,
                        "Request was interrupted",
                        List.of(new ErrorDetail(null,
                                "Thread interrupted")));
            }
        });
    }

    /**
     * Handles the HTTP response, mapping status codes to exceptions.
     * <p>
     * For 200 OK responses, also checks for cStat 678 rate-limit
     * in the SOAP body and throws {@code RATE_LIMIT} if detected.
     */
    private String handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String body = response.body();

        if (statusCode == 200) {
            // Check for cStat 678 rate limit in the SOAP response body
            if (xmlParser != null && body != null && !body.isBlank()) {
                try {
                    var parsed = xmlParser.parseResponse(body);
                    if (rateLimitBackoff.isRateLimit(parsed.cStat())) {
                        throw new NfcomException("RATE_LIMIT", 429,
                                "cStat 678 — Consumo Indevido",
                                List.of(new ErrorDetail(null,
                                        "Rate limit exceeded. SEFAZ returned cStat 678.")));
                    }
                } catch (IllegalArgumentException e) {
                    // XML parsing failed; likely a non-SOAP response.
                    // Return the body as-is; the caller will handle parsing.
                }
            }
            return body;
        }

        // Try to extract SOAP fault details from the response body
        String faultCode = null;
        String faultString = null;
        if (body != null && !body.isBlank()) {
            int codeStart = body.indexOf("<faultcode>");
            int codeEnd = body.indexOf("</faultcode>");
            if (codeStart >= 0 && codeEnd > codeStart) {
                faultCode = body.substring(codeStart + 11, codeEnd).trim();
            }
            int stringStart = body.indexOf("<faultstring>");
            int stringEnd = body.indexOf("</faultstring>");
            if (stringStart >= 0 && stringEnd > stringStart) {
                faultString = body.substring(stringStart + 13, stringEnd).trim();
            }
        }
        String faultDetail = (faultCode != null ? "[" + faultCode + "] " : "")
                + (faultString != null ? faultString : "");

        // Map HTTP error status codes to NfcomException
        switch (statusCode) {
            case 401:
            case 403:
                throw new NfcomException("SEFAZ_AUTH_ERROR", statusCode,
                        "SEFAZ authentication error (HTTP " + statusCode + ")",
                        List.of(new ErrorDetail(null,
                                faultDetail.isBlank()
                                        ? "Authentication failed with SEFAZ"
                                        : faultDetail)));
            case 503:
                throw new NfcomException("SEFAZ_UNAVAILABLE", statusCode,
                        "SEFAZ service unavailable (HTTP 503)",
                        List.of(new ErrorDetail(null,
                                faultDetail.isBlank()
                                        ? "cStat 108 — Serviço Indisponível"
                                        : faultDetail)));
            default:
                if (statusCode >= 400 && statusCode < 500) {
                    throw new NfcomException("SEFAZ_ERROR", statusCode,
                            "SEFAZ client error (HTTP " + statusCode + ")",
                            List.of(new ErrorDetail(null,
                                    faultDetail.isBlank()
                                            ? "cStat 999 — HTTP " + statusCode
                                            : faultDetail)));
                } else if (statusCode >= 500) {
                    throw new NfcomException("SEFAZ_ERROR", statusCode,
                            "SEFAZ server error (HTTP " + statusCode + ")",
                            List.of(new ErrorDetail(null,
                                    faultDetail.isBlank()
                                            ? "cStat 999 — HTTP " + statusCode
                                            : faultDetail)));
                }
                // Unexpected 2xx/3xx status
                return body;
        }
    }

    // ---------------------------------------------------------------
    // Convenience methods for each SEFAZ service
    // ---------------------------------------------------------------

    /**
     * Sends a GZip+Base64 compressed NFCom submission to SEFAZ.
     *
     * @param compressedBase64Body the GZip-compressed and Base64-encoded
     *                             NFCom signed XML
     * @return the SOAP response XML string
     */
    public String submitNfcom(String compressedBase64Body) {
        String envelope = soapMessageBuilder.buildRecepcaoEnvelope(compressedBase64Body);
        return send("NFComRecepcao", envelope);
    }

    /**
     * Sends an NFCom consulta request to SEFAZ.
     *
     * @param consultXml the consulta request XML (not SOAP-wrapped)
     * @return the SOAP response XML string
     */
    public String consultarNfcom(String consultXml) {
        String envelope = soapMessageBuilder.buildStandardEnvelope(consultXml,
                SERVICE_NAMESPACES.get("NFComConsulta"));
        return send("NFComConsulta", envelope);
    }

    /**
     * Sends a service status request to SEFAZ.
     *
     * @param statusXml the status request XML (not SOAP-wrapped)
     * @return the SOAP response XML string
     */
    public String consultarStatus(String statusXml) {
        String envelope = soapMessageBuilder.buildStandardEnvelope(statusXml,
                SERVICE_NAMESPACES.get("NFComStatusServico"));
        return send("NFComStatusServico", envelope);
    }

    /**
     * Sends an event registration request to SEFAZ.
     *
     * @param eventXml the event request XML (not SOAP-wrapped)
     * @return the SOAP response XML string
     */
    public String enviarEvento(String eventXml) {
        String envelope = soapMessageBuilder.buildStandardEnvelope(eventXml,
                SERVICE_NAMESPACES.get("NFComRecepcaoEvento"));
        return send("NFComRecepcaoEvento", envelope);
    }

    /**
     * Sends a taxpayer registration consulta request to SEFAZ.
     *
     * @param cadastroXml the taxpayer consulta XML (not SOAP-wrapped)
     * @return the SOAP response XML string
     */
    public String consultarCadastro(String cadastroXml) {
        String envelope = soapMessageBuilder.buildStandardEnvelope(cadastroXml,
                SERVICE_NAMESPACES.get("ConsultaCadastro"));
        return send("ConsultaCadastro", envelope);
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * Resolves the full endpoint URI for the given service name.
     * <p>
     * The base URL is taken from {@code nfcom.sefaz.url} configuration,
     * with the service name appended as a path segment.
     */
    private URI resolveEndpoint(String serviceName) {
        return URI.create(baseUrl + "/" + serviceName);
    }

    /**
     * Returns the unmodifiable service namespace mapping.
     * Exposed for testing and diagnostic purposes.
     */
    public static Map<String, String> getServiceNamespaces() {
        return SERVICE_NAMESPACES;
    }

    /**
     * Reads a configuration value by attempting system property first,
     * then falling back to environment variable, then default value.
     */
    private static String readConfig(String propertyName, String defaultValue) {
        String value = System.getProperty(propertyName);
        if (value != null && !value.isBlank()) {
            return value;
        }
        String envName = propertyName.toUpperCase().replace('.', '_');
        value = System.getenv(envName);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return defaultValue;
    }

    /**
     * Reads an integer configuration value with a default fallback.
     */
    private static int parseIntConfig(String propertyName, int defaultValue) {
        String value = readConfig(propertyName, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
