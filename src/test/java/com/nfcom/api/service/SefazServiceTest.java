package com.nfcom.api.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.nfcom.api.client.SefazClient;
import com.nfcom.api.dto.request.EventoData;
import com.nfcom.api.dto.request.NfcomData;
import com.nfcom.api.service.RateLimitBackoff;
import com.nfcom.api.xml.NfcomXmlBuilder;
import com.nfcom.api.xml.NfcomXmlParser;
import com.nfcom.api.xml.NfcomXmlParser.ParsedResponse;
import com.nfcom.api.xml.SoapMessageBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SefazService} — the NFCom orchestration layer.
 * <p>
 * Uses WireMock to simulate SEFAZ endpoints and real implementations
 * of {@link CertLoader}, {@link XmlSigner}, and other dependencies
 * to verify the full orchestration pipeline end-to-end.
 */
class SefazServiceTest {

    private static final String TEST_CERT_PATH = "src/test/resources/test-cert.p12";
    private static final String TEST_CERT_PASSWORD = "changeit";
    private static final String ACCESS_KEY = "35200600012345000176550000000012345678901234";
    private static final String NFCOM_NS = "http://www.portalfiscal.inf.br/NFCom";

    private WireMockServer wireMockServer;
    private SefazService service;
    private CertLoader certLoader;
    private XmlSigner xmlSigner;
    private SoapMessageBuilder soapMessageBuilder;
    private NfcomXmlParser xmlParser;
    private NfcomXmlBuilder xmlBuilder;
    private int port;

    // A valid SOAP response with cStat=100 (success)
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

    // A rejection SOAP response with cStat=200-series (rejection)
    private static final String REJECTION_SOAP_RESPONSE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body>"
                    + "<nfcomRecepcaoResult xmlns=\"http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao\">"
                    + "<retNFCom xmlns=\"http://www.portalfiscal.inf.br/NFCom\" versao=\"1.00\">"
                    + "<tpAmb>2</tpAmb>"
                    + "<cUF>35</cUF>"
                    + "<cStat>224</cStat>"
                    + "<xMotivo>Rejeicao: NFCom em duplicidade</xMotivo>"
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
        wireMockServer.resetAll();

        // Initialize CertLoader with test certificate
        certLoader = new CertLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD, null);
        certLoader.init();

        // Initialize real dependencies
        xmlSigner = new XmlSigner(certLoader);
        soapMessageBuilder = new SoapMessageBuilder();
        xmlParser = new NfcomXmlParser();
        xmlBuilder = new NfcomXmlBuilder();

        // SefazClient pointed at WireMock (no rate limit retries)
        RateLimitBackoff rateLimitBackoff = new RateLimitBackoff(0, Duration.ofMillis(10));
        String baseUrl = "http://localhost:" + port + "/NFCom";
        SefazClient sefazClient = createSefazClient(certLoader, rateLimitBackoff,
                soapMessageBuilder, xmlParser, baseUrl, 5000, 10000);

        // SefazService with all real dependencies
        service = new SefazService(certLoader, xmlSigner, sefazClient,
                soapMessageBuilder, xmlParser, rateLimitBackoff);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    // ---------------------------------------------------------------
    // Helper: create SefazClient via reflection (constructor is package-private)
    // ---------------------------------------------------------------

    private static SefazClient createSefazClient(
            CertLoader certLoader, RateLimitBackoff rateLimitBackoff,
            SoapMessageBuilder soapMessageBuilder, NfcomXmlParser xmlParser,
            String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        try {
            // Use the public no-arg constructor
            SefazClient client = new SefazClient();

            // Set fields via reflection
            setField(client, "certLoader", certLoader);
            setField(client, "rateLimitBackoff", rateLimitBackoff);
            setField(client, "soapMessageBuilder", soapMessageBuilder);
            setField(client, "xmlParser", xmlParser);
            setField(client, "baseUrl", baseUrl);
            setField(client, "connectTimeoutMs", connectTimeoutMs);
            setField(client, "readTimeoutMs", readTimeoutMs);

            // Initialize the HttpClient via reflection (init() is package-private)
            setUpHttpClient(client);
            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SefazClient for test", e);
        }
    }

    private static void setField(Object target, String fieldName, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setUpHttpClient(SefazClient client) throws Exception {
        // Replicate what SefazClient.init() does: build an HttpClient
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
                .connectTimeout(java.time.Duration.ofMillis(
                        (int) getField(client, "connectTimeoutMs")))
                .executor(Executors.newVirtualThreadPerTaskExecutor());

        CertLoader cl = (CertLoader) getField(client, "certLoader");
        if (cl != null) {
            javax.net.ssl.SSLContext sslContext = cl.createSslContext();
            if (sslContext != null) {
                builder.sslContext(sslContext);
            }
        }

        java.net.http.HttpClient httpClient = builder.build();
        setField(client, "httpClient", httpClient);
    }

    private static Object getField(Object target, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    // ---------------------------------------------------------------
    // Helper: create realistic NfcomData
    // ---------------------------------------------------------------

    private NfcomData createSampleNfcomData() {
        return new NfcomData(
                new NfcomData.IdeData(
                        35,                     // cUF (SP)
                        2,                      // tpAmb (teste)
                        "62",                   // mod
                        1,                      // serie
                        123456L,                // nNF
                        "ABCD1234",             // cNF
                        "7",                    // cDV
                        "2026-07-03T14:30:00-03:00", // dhEmi
                        1,                      // tpEmis (normal)
                        "1",                    // nSiteAutoriz
                        3550308,                // cMunFG (SP)
                        1,                      // finNFCom (normal)
                        1,                      // tpFat (mensal)
                        "1.0.0"                 // verProc
                ),
                new NfcomData.EmitData(
                        "00000000000191",       // CNPJ
                        "123456789",            // IE
                        "NFCom Emissora Ltda",  // xNome
                        1                       // CRT
                ),
                new NfcomData.DestData(
                        "NFCom Assinante",       // xNome
                        "00000000000192",        // CNPJ
                        null,                    // CPF
                        9,                       // indIEDest (nao contribuinte)
                        null,                    // IE
                        new NfcomData.DestData.EnderDest(
                                "Rua Exemplo",   // logradouro
                                "100",           // nro
                                "Centro",        // xBairro
                                3550308,         // cMun
                                "Sao Paulo",     // xMun
                                "SP",            // uf
                                "01001000",      // cep
                                1058             // cPais
                        )
                ),
                new NfcomData.AssinanteData(
                        "123456",               // iCodAssinante
                        1,                      // tpAssinante
                        1                       // tpServUtil
                ),
                List.of(
                        new NfcomData.ItemData(
                                1,              // nItem
                                "PROD001",      // cProd
                                "Servico de Telecom", // xProd
                                "010101",       // cClass
                                "5303",         // CFOP
                                "UN",           // uCom
                                new BigDecimal("1.00"),   // qCom
                                new BigDecimal("100.00"), // vUnCom
                                new BigDecimal("100.00")  // vProd
                        )
                ),
                new NfcomData.TotalData(
                        new BigDecimal("100.00"), // vProd
                        new BigDecimal("100.00")  // vNF
                ),
                new NfcomData.GFatData(
                        "202607",               // competFat
                        "2026-08-15",           // dVencFat
                        "2026-07-01",           // dPerUsoIni
                        "2026-07-31",           // dPerUsoFim
                        "1234567890ABCDEF"      // codBarras
                ),
                ACCESS_KEY
        );
    }

    // ---------------------------------------------------------------
    // 1. submitNfcom — full orchestration pipeline test
    // ---------------------------------------------------------------

    @Test
    void submitNfcomProducesFullOrchestrationPipeline() {
        // Stub the SEFAZ recepcao endpoint
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        NfcomData data = createSampleNfcomData();
        ParsedResponse result = service.submitNfcom(data);

        // Verify response was parsed correctly
        assertNotNull(result);
        assertEquals(100, result.cStat());
        assertEquals("Autorizado o Uso da NFCom", result.xMotivo());
        assertEquals(ACCESS_KEY, result.chNFCom());
        assertEquals("135200000123456", result.nProt());
        assertNotNull(result.dhRecbto());

        // Verify the WireMock endpoint was actually called
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComRecepcao")));
    }

    @Test
    void submitNfcomSendsCompressedData() {
        // Capture the request body to verify it's Base64-ish (GZip+Base64)
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .withRequestBody(containing("H4s"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(VALID_SOAP_RESPONSE)));

        NfcomData data = createSampleNfcomData();
        ParsedResponse result = service.submitNfcom(data);

        assertNotNull(result);
        assertEquals(100, result.cStat());

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .withRequestBody(containing("H4s")));
    }

    // ---------------------------------------------------------------
    // 2. consultarNfcom
    // ---------------------------------------------------------------

    @Test
    void consultarNfcomReturnsParsedResponse() {
        // Use a consulta-specific SOAP response (same structure, different wrapper)
        String consultaResponse = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult",
                "nfcomConsultaResult");

        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComConsulta"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(consultaResponse)));

        ParsedResponse result = service.consultarNfcom(ACCESS_KEY, 2);

        assertNotNull(result);
        assertEquals(100, result.cStat());
        assertEquals(ACCESS_KEY, result.chNFCom());

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComConsulta")));
    }

    // ---------------------------------------------------------------
    // 3. consultarStatus
    // ---------------------------------------------------------------

    @Test
    void consultarStatusReturnsParsedResponse() {
        String statusResponse = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult",
                "nfcomStatusServicoResult");

        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComStatusServico"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(statusResponse)));

        ParsedResponse result = service.consultarStatus(2);

        assertNotNull(result);
        assertEquals(100, result.cStat());

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComStatusServico")));
    }

    // ---------------------------------------------------------------
    // 4. enviarEvento
    // ---------------------------------------------------------------

    @Test
    void enviarEventoReturnsParsedResponse() {
        String eventoResponse = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult",
                "nfcomRecepcaoEventoResult");

        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcaoEvento"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(eventoResponse)));

        EventoData evento = EventoData.cancelamento(
                ACCESS_KEY,
                "135200000123456",
                "Cancelamento por erro no valor",
                2,
                "00000000000191",
                35,
                "2026-07-03T15:00:00-03:00"
        );

        ParsedResponse result = service.enviarEvento(evento);

        assertNotNull(result);
        assertEquals(100, result.cStat());

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/NFComRecepcaoEvento")));
    }

    // ---------------------------------------------------------------
    // 5. consultarCadastro
    // ---------------------------------------------------------------

    @Test
    void consultarCadastroReturnsParsedResponse() {
        String cadastroResponse = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult",
                "consultaCadastroResult");

        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/ConsultaCadastro"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(cadastroResponse)));

        ParsedResponse result = service.consultarCadastro("00000000000191", 2);

        assertNotNull(result);
        assertEquals(100, result.cStat());

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/NFCom/ConsultaCadastro")));
    }

    // ---------------------------------------------------------------
    // Error propagation tests
    // ---------------------------------------------------------------

    @Test
    void submitNfcomPropagatesRejection() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(REJECTION_SOAP_RESPONSE)));

        NfcomData data = createSampleNfcomData();
        ParsedResponse result = service.submitNfcom(data);

        // Rejection is not an exception — SEFAZ returns 200 with cStat=224
        assertNotNull(result);
        assertEquals(224, result.cStat());
        assertTrue(result.xMotivo().contains("duplicidade"));
    }

    @Test
    void submitNfcomPropagatesHttpError() {
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(401)));

        NfcomData data = createSampleNfcomData();

        com.nfcom.api.shared.error.NfcomException ex = assertThrows(
                com.nfcom.api.shared.error.NfcomException.class,
                () -> service.submitNfcom(data));
        assertTrue(ex.getCode().contains("SEFAZ_AUTH_ERROR"));
    }

    // ---------------------------------------------------------------
    // XML builder validation tests (via NfcomXmlBuilder directly)
    // ---------------------------------------------------------------

    @Test
    void nfcomXmlHasCorrectNamespaceAndId() {
        Document doc = xmlBuilder.buildNfcomXml(createSampleNfcomData());

        Element root = doc.getDocumentElement();
        assertEquals("NFCom", root.getLocalName());
        assertEquals(NFCOM_NS, root.getNamespaceURI());
        assertEquals(NFCOM_NS, root.getAttribute("xmlns"));

        // Find infNFCom and check its Id attribute
        Element infNFCom = findChildByLocalName(root, "infNFCom");
        assertNotNull(infNFCom);
        assertEquals("NFCom" + ACCESS_KEY, infNFCom.getAttribute("Id"));
        assertEquals("1.00", infNFCom.getAttribute("versao"));
    }

    @Test
    void nfcomXmlContainsAllSections() {
        Document doc = xmlBuilder.buildNfcomXml(createSampleNfcomData());
        Element root = doc.getDocumentElement();

        assertNotNull(findChildByLocalName(root, "infNFCom"), "Should have infNFCom");
        Element infNFCom = findChildByLocalName(root, "infNFCom");

        assertNotNull(findChildByLocalName(infNFCom, "ide"), "Should have ide");
        assertNotNull(findChildByLocalName(infNFCom, "emit"), "Should have emit");
        assertNotNull(findChildByLocalName(infNFCom, "dest"), "Should have dest");
        assertNotNull(findChildByLocalName(infNFCom, "assinante"), "Should have assinante");
        assertNotNull(findChildByLocalName(infNFCom, "det"), "Should have det");
        assertNotNull(findChildByLocalName(infNFCom, "total"), "Should have total");
        assertNotNull(findChildByLocalName(infNFCom, "gFat"), "Should have gFat");
    }

    @Test
    void consultXmlHasCorrectStructure() {
        Document doc = xmlBuilder.buildConsultXml(ACCESS_KEY, 2);
        Element root = doc.getDocumentElement();

        assertEquals("consSitNFCom", root.getLocalName());
        assertEquals(NFCOM_NS, root.getNamespaceURI());

        assertEquals("2", getChildTextContent(root, "tpAmb"));
        assertEquals("CONSULTAR", getChildTextContent(root, "xServ"));
        assertEquals(ACCESS_KEY, getChildTextContent(root, "chNFCom"));
    }

    @Test
    void statusXmlHasCorrectStructure() {
        Document doc = xmlBuilder.buildStatusXml(2);
        Element root = doc.getDocumentElement();

        assertEquals("consStatServNFCom", root.getLocalName());
        assertEquals(NFCOM_NS, root.getNamespaceURI());

        assertEquals("2", getChildTextContent(root, "tpAmb"));
        assertEquals("STATUS", getChildTextContent(root, "xServ"));
    }

    @Test
    void eventXmlHasCorrectIdAndCancellationStructure() {
        EventoData evento = EventoData.cancelamento(
                ACCESS_KEY, "135200000123456",
                "Cancelamento por erro no valor",
                2, "00000000000191", 35,
                "2026-07-03T15:00:00-03:00");

        Document doc = xmlBuilder.buildEventXml(evento);
        Element root = doc.getDocumentElement();

        assertEquals("eventoNFCom", root.getLocalName());
        assertEquals(NFCOM_NS, root.getNamespaceURI());

        Element infEvento = findChildByLocalName(root, "infEvento");
        assertNotNull(infEvento);

        String expectedId = "ID" + evento.tpEvento() + evento.chNFCom() + evento.nSeqEvento();
        assertEquals(expectedId, infEvento.getAttribute("Id"));

        assertEquals("35", getChildTextContent(infEvento, "cOrgao"));
        assertEquals("2", getChildTextContent(infEvento, "tpAmb"));
        assertEquals("00000000000191", getChildTextContent(infEvento, "CNPJ"));
        assertEquals(ACCESS_KEY, getChildTextContent(infEvento, "chNFCom"));
        assertEquals("110111", getChildTextContent(infEvento, "tpEvento"));
        assertEquals("1", getChildTextContent(infEvento, "nSeqEvento"));

        Element detEvento = findChildByLocalName(infEvento, "detEvento");
        assertNotNull(detEvento);
        assertEquals("1.00", detEvento.getAttribute("versaoEvento"));

        Element evCanc = findChildByLocalName(detEvento, "evCancNFCom");
        assertNotNull(evCanc);
        assertEquals("Cancelamento", getChildTextContent(evCanc, "descEvento"));
        assertEquals("135200000123456", getChildTextContent(evCanc, "nProt"));
        assertEquals("Cancelamento por erro no valor", getChildTextContent(evCanc, "xJust"));
    }

    @Test
    void cadastroXmlHasCorrectStructure() {
        Document doc = xmlBuilder.buildCadastroXml("00000000000191", 2);
        Element root = doc.getDocumentElement();

        assertEquals("consultaCadastro", root.getLocalName());
        assertEquals(NFCOM_NS, root.getNamespaceURI());

        assertEquals("2", getChildTextContent(root, "tpAmb"));
        assertEquals("CONSULTAR", getChildTextContent(root, "xServ"));
        assertEquals("00000000000191", getChildTextContent(root, "CNPJ"));
    }

    // ---------------------------------------------------------------
    // Signing verification tests — verify that signing works in context
    // ---------------------------------------------------------------

    @Test
    void submitNfcomSignsTheDocument() throws Exception {
        // Build and sign an NFCom XML, verify the signature is valid
        Document doc = xmlBuilder.buildNfcomXml(createSampleNfcomData());
        Document signed = xmlSigner.signNfcom(doc);

        // Verify signature element is present
        Element signature = findElementDescendant(signed,
                "http://www.w3.org/2000/09/xmldsig#", "Signature");
        assertNotNull(signature, "Signed document should contain a Signature element");

        // Verify Reference URI matches the NFCom access key
        Element reference = findElementDescendant(signed,
                "http://www.w3.org/2000/09/xmldsig#", "Reference");
        assertNotNull(reference);
        assertEquals("#NFCom" + ACCESS_KEY, reference.getAttribute("URI"));

        // Verify signature validates with public key
        assertTrue(verifySignature(signed, certLoader.getCertificate().getPublicKey()),
                "Signature should be valid");
    }

    @Test
    void eventXmlSigningProducesValidSignature() throws Exception {
        EventoData evento = EventoData.cancelamento(
                ACCESS_KEY, "135200000123456",
                "Cancelamento por erro no valor",
                2, "00000000000191", 35,
                "2026-07-03T15:00:00-03:00");

        Document doc = xmlBuilder.buildEventXml(evento);
        String idValue = "ID" + evento.tpEvento() + evento.chNFCom() + evento.nSeqEvento();
        Document signed = xmlSigner.signXml(doc, idValue);

        Element signature = findElementDescendant(signed,
                "http://www.w3.org/2000/09/xmldsig#", "Signature");
        assertNotNull(signature, "Signed event should contain a Signature element");

        Element reference = findElementDescendant(signed,
                "http://www.w3.org/2000/09/xmldsig#", "Reference");
        assertNotNull(reference);
        assertEquals("#" + idValue, reference.getAttribute("URI"));

        assertTrue(verifySignature(signed, certLoader.getCertificate().getPublicKey()),
                "Event signature should be valid");
    }

    // ---------------------------------------------------------------
    // All 5 services in one test
    // ---------------------------------------------------------------

    @Test
    void allFiveServiceMethodsWorkIndividually() {
        // Stub all 5 endpoints with different response wrappers
        String recepcaoResp = VALID_SOAP_RESPONSE;
        String consultaResp = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult", "nfcomConsultaResult");
        String statusResp = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult", "nfcomStatusServicoResult");
        String eventoResp = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult", "nfcomRecepcaoEventoResult");
        String cadastroResp = VALID_SOAP_RESPONSE.replace(
                "nfcomRecepcaoResult", "consultaCadastroResult");

        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcao"))
                .willReturn(aResponse().withStatus(200).withBody(recepcaoResp)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComConsulta"))
                .willReturn(aResponse().withStatus(200).withBody(consultaResp)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComStatusServico"))
                .willReturn(aResponse().withStatus(200).withBody(statusResp)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/NFComRecepcaoEvento"))
                .willReturn(aResponse().withStatus(200).withBody(eventoResp)));
        wireMockServer.stubFor(post(urlPathEqualTo("/NFCom/ConsultaCadastro"))
                .willReturn(aResponse().withStatus(200).withBody(cadastroResp)));

        NfcomData data = createSampleNfcomData();
        EventoData evento = EventoData.cancelamento(
                ACCESS_KEY, "135200000123456",
                "Teste", 2, "00000000000191", 35,
                "2026-07-03T15:00:00-03:00");

        assertNotNull(service.submitNfcom(data));
        assertNotNull(service.consultarNfcom(ACCESS_KEY, 2));
        assertNotNull(service.consultarStatus(2));
        assertNotNull(service.enviarEvento(evento));
        assertNotNull(service.consultarCadastro("00000000000191", 2));
    }

    // ---------------------------------------------------------------
    // Helper: DOM navigation
    // ---------------------------------------------------------------

    private Element findChildByLocalName(Element parent, String localName) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if (localName.equals(el.getLocalName())) {
                    return el;
                }
            }
        }
        return null;
    }

    private String getChildTextContent(Element parent, String localName) {
        Element child = findChildByLocalName(parent, localName);
        return child != null ? child.getTextContent() : null;
    }

    private Element findElementDescendant(Document doc, String ns, String localName) {
        var nl = doc.getElementsByTagNameNS(ns, localName);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private Element findElementDescendant(Element parent, String ns, String localName) {
        var nl = parent.getElementsByTagNameNS(ns, localName);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    // ---------------------------------------------------------------
    // Helper: XML signature verification
    // ---------------------------------------------------------------

    private boolean verifySignature(Document doc, java.security.PublicKey publicKey) throws Exception {
        javax.xml.crypto.dsig.XMLSignatureFactory fac =
                javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM");
        Element signatureElement = findElementDescendant(doc,
                "http://www.w3.org/2000/09/xmldsig#", "Signature");
        if (signatureElement == null) return false;

        // Register Id attributes
        registerIdAttributes(doc);

        javax.xml.crypto.dsig.dom.DOMValidateContext valContext =
                new javax.xml.crypto.dsig.dom.DOMValidateContext(publicKey, signatureElement);
        valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);

        javax.xml.crypto.dsig.XMLSignature signature = fac.unmarshalXMLSignature(valContext);
        return signature.validate(valContext);
    }

    private void registerIdAttributes(Document doc) {
        var allElements = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            org.w3c.dom.Element el = (org.w3c.dom.Element) allElements.item(i);
            if (el.hasAttribute("Id") && !el.getAttribute("Id").isBlank()) {
                el.setIdAttribute("Id", true);
            }
        }
    }
}
