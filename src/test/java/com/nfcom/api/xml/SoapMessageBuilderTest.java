package com.nfcom.api.xml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapMessageBuilderTest {

    private SoapMessageBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SoapMessageBuilder();
    }

    @Test
    void buildSoapEnvelopeContainsSoapNamespace() {
        String body = "<nfcom>teste</nfcom>";
        String ns = "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao";
        String envelope = builder.buildSoapEnvelope(body, ns);

        assertNotNull(envelope);
        assertTrue(envelope.contains("http://www.w3.org/2003/05/soap-envelope"),
                "Should contain SOAP 1.2 envelope namespace");
    }

    @Test
    void buildSoapEnvelopeContainsBodyContent() {
        String body = "<nfcom><cUF>35</cUF></nfcom>";
        String ns = "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao";
        String envelope = builder.buildSoapEnvelope(body, ns);

        assertTrue(envelope.contains(body), "Envelope should contain the body content");
    }

    @Test
    void buildRecepcaoEnvelopeContainsCompressedContent() {
        String compressedBase64 = "H4sIAAAAAAAAAEWMPQvCMBQA";
        String envelope = builder.buildRecepcaoEnvelope(compressedBase64);

        assertNotNull(envelope);
        assertTrue(envelope.contains(compressedBase64),
                "Envelope should contain the compressed Base64 payload");
        assertTrue(envelope.contains("NFComRecepcao"),
                "Envelope should reference NFComRecepcao service");
    }

    @Test
    void buildStandardEnvelopeContainsXmlBody() {
        String xmlBody = "<consStatServNFC xmlns=\"http://www.portalfiscal.inf.br/NFCom\"><tpAmb>2</tpAmb></consStatServNFC>";
        String ns = "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComStatusServico";
        String envelope = builder.buildStandardEnvelope(xmlBody, ns);

        assertNotNull(envelope);
        assertTrue(envelope.contains(xmlBody),
                "Envelope should contain the XML body");
        assertTrue(envelope.contains("NFComStatusServico"),
                "Envelope should reference the status service");
    }

    @Test
    void buildStandardEnvelopeWithConsultaService() {
        String xmlBody = "<consNFC NFCom=\"http://www.portalfiscal.inf.br/NFCom\"><chNFCom>35200600012345000176550000000012345678901234</chNFCom></consNFC>";
        String ns = "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComConsulta";
        String envelope = builder.buildStandardEnvelope(xmlBody, ns);

        assertTrue(envelope.contains("NFComConsulta"));
    }

    @Test
    void envelopeIsValidXml() throws Exception {
        String envelope = builder.buildRecepcaoEnvelope("H4sIAAAAAA");
        // Should parse without exception
        javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void envelopeHasSoapBodyAndHeader() {
        String envelope = builder.buildStandardEnvelope("<test/>", "http://example.com/wsdl/Service");
        assertTrue(envelope.contains("soap:Body"), "Should have soap:Body element");
        assertTrue(envelope.contains("soap:Header"), "Should have soap:Header element");
    }

    @Test
    void buildSoapEnvelopeWithNullNamespace() {
        String body = "<test>data</test>";
        String envelope = builder.buildSoapEnvelope(body, null);
        assertNotNull(envelope);
        assertTrue(envelope.contains(body));
    }
}
