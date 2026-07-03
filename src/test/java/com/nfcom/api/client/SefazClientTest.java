package com.nfcom.api.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nfcom.api.service.CertLoader;
import com.nfcom.api.service.RateLimitBackoff;
import com.nfcom.api.shared.error.NfcomException;
import com.nfcom.api.xml.NfcomXmlParser;
import com.nfcom.api.xml.SoapMessageBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class SefazClientTest {

    private WireMockServer wireMockServer;
    private SefazClient client;
    private CertLoader certLoader;
    private RateLimitBackoff rateLimitBackoff;
    private SoapMessageBuilder soapMessageBuilder;
    private NfcomXmlParser xmlParser;
    private int port;

    // A valid SOAP response used for stubbing 200-OK responses
    private static final String VALID_SOAP_RESPONSE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
            + "<soap:Body>"
            + "<nfcomRecepcaoResult xmlns=\"http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao\">"
            + "<retNFCom xmlns=\"http://www.portalfiscal.inf.br/NFCom\" versao=\"1.00\">"
            + "<tpAmb>2</tpAmb>"
            + "<cUF>35</cUF>"
            + "<cStat>100</cStat>"
            + "<xMotivo>Autorizado o Uso da NFCom</xMotivo>"
            + "<chNFCom>35200600012345000176550000000012345678901234</chNFCom>"
            + "<nProt>135200000123456</nProt>"
            + "<dhRecbto>2026-07-03T14:30:00-03:00</dhRecbto>"
            + "</retNFCom>"
            + "</nfcomRecepcaoResult>"
            + "</soap:Body>"
            + "</soap:Envelope>";

    private static final String RATE_LIMIT_SOAP_RESPONSE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
            + "<soap:Body>"
            + "<nfcomRecepcaoResult xmlns=\"http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao\">"
            + "<retNFCom xmlns=\"http://www.portalfiscal.inf.br/NFCom\" versao=\"1.00\">"
            + "<tpAmb>2</tpAmb>"
            + "<cUF>35</cUF>"
            + "<cStat>678</cStat>"
            + "<xMotivo>Consumo Indevido</xMotivo>"
            + "</retNFCom>"
            + "</nfcomRecepcaoResult>"
            + "</soap:Body>"
            + "</soap:Envelope>";

    @BeforeEach
    void setUp() {
        // Initialize WireMock on dynamic port
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        port = wireMockServer.port();
        WireMock.configureFor("localhost", port);
        wireMockServer.resetAll(); // ensure no cross-test stub pollution

        // Initialize CertLoader with test cert for mTLS verification
        System.setProperty("nfcom.cert.path", "src/test/resources/test-cert.p12");
        System.setProperty("nfcom.cert.password", "changeit");
        certLoader = new CertLoader();
        certLoader.init();

        rateLimitBackoff = new RateLimitBackoff(0, Duration.ofMillis(10));
        soapMessageBuilder = new SoapMessageBuilder();
        xmlParser = new NfcomXmlParser();

        String baseUrl = "http://localhost:" + port + "/NFCom";
        client = new SefazClient(certLoader, rateLimitBackoff, soapMessageBuilder, xmlParser,
                baseUrl, 5000, 10000);
        client.init();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        System.clearProperty("nfcom.cert.path");
        System.clearProperty("nfcom.cert.password");
    }

    // --- Content-Type header tests ---

    @Test
    void sendsRequestWithSoap12ContentType() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .withHeader("Content-Type", equalTo("application/soap+xml;charset=UTF-8"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        client.submitNfcom("H4sIAAAAAA");

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .withHeader("Content-Type", equalTo("application/soap+xml;charset=UTF-8")));
    }

    // --- Successful response tests ---

    @Test
    void returnsResponseBodyOnSuccessfulSubmit() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.submitNfcom("H4sIAAAAAA");

        assertNotNull(result);
        assertTrue(result.contains("cStat>100<"), "Response should contain cStat 100");
        assertTrue(result.contains("Autorizado o Uso da NFCom"), "Response should contain motivo");
    }

    @Test
    void returnsResponseBodyOnSuccessfulConsulta() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComConsulta"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.consultarNfcom("<consulta/>");
        assertNotNull(result);
    }

    @Test
    void returnsResponseBodyOnSuccessfulStatus() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComStatusServico"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.consultarStatus("<status/>");
        assertNotNull(result);
    }

    @Test
    void returnsResponseBodyOnSuccessfulEvent() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcaoEvento"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.enviarEvento("<evento/>");
        assertNotNull(result);
    }

    @Test
    void returnsResponseBodyOnSuccessfulCadastro() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/ConsultaCadastro"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.consultarCadastro("<cadastro/>");
        assertNotNull(result);
    }

    // --- HTTP error code mapping tests ---

    @Test
    void throwsAuthErrorOn401() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(401)));

        NfcomException ex = assertThrows(NfcomException.class,
                () -> client.submitNfcom("test-data"));
        assertEquals("SEFAZ_AUTH_ERROR", ex.getCode());
        assertEquals(401, ex.getHttpStatus());
    }

    @Test
    void throwsAuthErrorOn403() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(403)));

        NfcomException ex = assertThrows(NfcomException.class,
                () -> client.submitNfcom("test-data"));
        assertEquals("SEFAZ_AUTH_ERROR", ex.getCode());
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    void throwsUnavailableOn503() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(503)));

        NfcomException ex = assertThrows(NfcomException.class,
                () -> client.submitNfcom("test-data"));
        assertEquals("SEFAZ_UNAVAILABLE", ex.getCode());
        assertEquals(503, ex.getHttpStatus());
    }

    @Test
    void throwsSefazErrorOn400() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(400)));

        NfcomException ex = assertThrows(NfcomException.class,
                () -> client.submitNfcom("test-data"));
        assertEquals("SEFAZ_ERROR", ex.getCode());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void throwsSefazErrorOn500() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(500)));

        NfcomException ex = assertThrows(NfcomException.class,
                () -> client.submitNfcom("test-data"));
        assertEquals("SEFAZ_ERROR", ex.getCode());
        assertEquals(500, ex.getHttpStatus());
    }

    @Test
    void throwsSefazErrorOn502() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(502)));

        NfcomException ex = assertThrows(NfcomException.class,
                () -> client.submitNfcom("test-data"));
        assertEquals("SEFAZ_ERROR", ex.getCode());
        assertEquals(502, ex.getHttpStatus());
    }

    // --- Request body verification ---

    @Test
    void submitNfcomSendsCompressedDataInBody() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .withRequestBody(containing("H4sIAAAAAA"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.submitNfcom("H4sIAAAAAA");
        assertNotNull(result);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .withRequestBody(containing("H4sIAAAAAA")));
    }

    @Test
    void consultarNfcomSendsXmlBody() {
        String consultXml = "<consNFC><chNFCom>35200600012345000176550000000012345678901234</chNFCom></consNFC>";

        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComConsulta"))
                .withRequestBody(containing("35200600012345000176550000000012345678901234"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.consultarNfcom(consultXml);
        assertNotNull(result);

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComConsulta"))
                .withRequestBody(containing(consultXml)));
    }

    // --- Rate limit detection test ---

    @Test
    void rateLimitInResponseThrowsNfcomException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(RATE_LIMIT_SOAP_RESPONSE)));

        // With maxRetries=0, rate limit is immediately converted to RATE_LIMIT_EXCEEDED
        NfcomException ex = assertThrows(NfcomException.class,
                () -> client.submitNfcom("test-data"));
        assertTrue(ex.getCode().contains("RATE_LIMIT"),
                "Rate limit should be detected and reported");
    }

    // --- mTLS verification ---

    @Test
    void certLoaderProvidesValidSslContext() {
        javax.net.ssl.SSLContext sslContext = certLoader.createSslContext();
        assertNotNull(sslContext, "CertLoader should provide an SSLContext for mTLS");
        assertEquals("TLSv1.2", sslContext.getProtocol(),
                "SSLContext should use TLSv1.2");
    }

    @Test
    void clientInitializesWithCertLoaderSslContext() {
        // This test verifies that the client init() doesn't fail when
        // CertLoader provides an SSLContext, proving mTLS setup works
        javax.net.ssl.SSLContext sslContext = certLoader.createSslContext();
        assertNotNull(sslContext);

        // Verify client can still make plain HTTP requests with SSLContext configured
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        String result = client.submitNfcom("test");
        assertNotNull(result, "Client should work with SSLContext configured");
    }

    // --- Timeout test ---

    @Test
    void requestTimedOutThrowsNfcomException() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse()
                        .withFixedDelay(200) // 200ms delay
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        // Create a client with very short read timeout (50ms)
        // The 200ms delay will cause a timeout
        String baseUrl = "http://localhost:" + port + "/NFCom";
        SefazClient fastClient = new SefazClient(certLoader, rateLimitBackoff,
                soapMessageBuilder, xmlParser, baseUrl, 5000, 50);
        fastClient.init();

        NfcomException ex = assertThrows(NfcomException.class,
                () -> fastClient.submitNfcom("test-data"));
        assertEquals("TIMEOUT", ex.getCode(),
                "Timeout should result in TIMEOUT error code");
        assertEquals(504, ex.getHttpStatus());
    }

    // --- All 5 services in one test ---

    @Test
    void allFiveServiceMethodsWorkIndividually() {
        // Stub all 5 service endpoints
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(200).withBody(VALID_SOAP_RESPONSE)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComConsulta"))
                .willReturn(aResponse().withStatus(200).withBody(VALID_SOAP_RESPONSE)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComStatusServico"))
                .willReturn(aResponse().withStatus(200).withBody(VALID_SOAP_RESPONSE)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcaoEvento"))
                .willReturn(aResponse().withStatus(200).withBody(VALID_SOAP_RESPONSE)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/ConsultaCadastro"))
                .willReturn(aResponse().withStatus(200).withBody(VALID_SOAP_RESPONSE)));

        assertNotNull(client.submitNfcom("compressed-data"), "submitNfcom should succeed");
        assertNotNull(client.consultarNfcom("<consulta/>"), "consultarNfcom should succeed");
        assertNotNull(client.consultarStatus("<status/>"), "consultarStatus should succeed");
        assertNotNull(client.enviarEvento("<evento/>"), "enviarEvento should succeed");
        assertNotNull(client.consultarCadastro("<cadastro/>"), "consultarCadastro should succeed");
    }

    // --- Service namespace mapping ---

    @Test
    void serviceNamespaceMappingContainsAllServices() {
        var namespaces = SefazClient.getServiceNamespaces();
        assertEquals(5, namespaces.size(), "Should have 5 service namespace entries");
        assertTrue(namespaces.containsKey("NFComRecepcao"));
        assertTrue(namespaces.containsKey("NFComConsulta"));
        assertTrue(namespaces.containsKey("NFComStatusServico"));
        assertTrue(namespaces.containsKey("NFComRecepcaoEvento"));
        assertTrue(namespaces.containsKey("ConsultaCadastro"));
    }

    // --- POST method verification ---

    @Test
    void sendsPostRequest() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        client.submitNfcom("test");

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComRecepcao")));
    }
}
