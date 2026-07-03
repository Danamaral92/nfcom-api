package com.nfcom.api.xml;

import com.nfcom.api.xml.NfcomXmlParser.ParsedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NfcomXmlParserTest {

    private NfcomXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new NfcomXmlParser();
    }

    @Test
    void parsesSubmitResponseSuccessfully() {
        String soapXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
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

        ParsedResponse result = parser.parseResponse(soapXml);

        assertNotNull(result);
        assertEquals(100, result.cStat());
        assertEquals("Autorizado o Uso da NFCom", result.xMotivo());
        assertEquals("35200600012345000176550000000012345678901234", result.chNFCom());
        assertEquals("135200000123456", result.nProt());
        assertEquals("2026-07-03T14:30:00-03:00", result.dhRecbto());
    }

    @Test
    void parsesConsultaResponseSuccessfully() {
        String soapXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap:Body>"
                + "<nfcomConsultaResult xmlns=\"http://www.portalfiscal.inf.br/NFCom/wsdl/NFComConsulta\">"
                + "<retNFCom xmlns=\"http://www.portalfiscal.inf.br/NFCom\" versao=\"1.00\">"
                + "<tpAmb>2</tpAmb>"
                + "<cUF>35</cUF>"
                + "<cStat>100</cStat>"
                + "<xMotivo>Autorizado</xMotivo>"
                + "<chNFCom>35200600012345000176550000000012345678901234</chNFCom>"
                + "<nProt>135200000123456</nProt>"
                + "<dhRecbto>2026-07-03T14:30:00-03:00</dhRecbto>"
                + "</retNFCom>"
                + "</nfcomConsultaResult>"
                + "</soap:Body>"
                + "</soap:Envelope>";

        ParsedResponse result = parser.parseResponse(soapXml);

        assertNotNull(result);
        assertEquals(100, result.cStat());
        assertEquals("Autorizado", result.xMotivo());
        assertEquals("35200600012345000176550000000012345678901234", result.chNFCom());
    }

    @Test
    void parsesStatusResponseSuccessfully() {
        String soapXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap:Body>"
                + "<nfcomStatusServicoResult xmlns=\"http://www.portalfiscal.inf.br/NFCom/wsdl/NFComStatusServico\">"
                + "<retConsStatServNFC xmlns=\"http://www.portalfiscal.inf.br/NFCom\" versao=\"1.00\">"
                + "<tpAmb>2</tpAmb>"
                + "<cUF>35</cUF>"
                + "<cStat>107</cStat>"
                + "<xMotivo>Servico em Operacao</xMotivo>"
                + "<dhRetorno>2026-07-03T14:30:00-03:00</dhRetorno>"
                + "<tMed>5</tMed>"
                + "</retConsStatServNFC>"
                + "</nfcomStatusServicoResult>"
                + "</soap:Body>"
                + "</soap:Envelope>";

        ParsedResponse result = parser.parseResponse(soapXml);

        assertNotNull(result);
        assertEquals(107, result.cStat());
        assertEquals("Servico em Operacao", result.xMotivo());
        assertNull(result.chNFCom(), "Status response should not have chNFCom");
        assertNull(result.nProt(), "Status response should not have nProt");
    }

    @Test
    void parsesEventResponseSuccessfully() {
        String soapXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap:Body>"
                + "<nfcomRecepcaoEventoResult xmlns=\"http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcaoEvento\">"
                + "<retEventoNFCom xmlns=\"http://www.portalfiscal.inf.br/NFCom\" versao=\"1.00\">"
                + "<tpAmb>2</tpAmb>"
                + "<cUF>35</cUF>"
                + "<cStat>135</cStat>"
                + "<xMotivo>Evento registrado e vinculado a NFCom</xMotivo>"
                + "<chNFCom>35200600012345000176550000000012345678901234</chNFCom>"
                + "<nProt>135200000123456</nProt>"
                + "<dhRecbto>2026-07-03T15:00:00-03:00</dhRecbto>"
                + "</retEventoNFCom>"
                + "</nfcomRecepcaoEventoResult>"
                + "</soap:Body>"
                + "</soap:Envelope>";

        ParsedResponse result = parser.parseResponse(soapXml);

        assertNotNull(result);
        assertEquals(135, result.cStat());
        assertTrue(result.xMotivo().contains("Evento registrado"));
        assertEquals("35200600012345000176550000000012345678901234", result.chNFCom());
        assertEquals("135200000123456", result.nProt());
    }

    @Test
    void handlesErrorResponseWithLowerCStat() {
        String soapXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
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

        ParsedResponse result = parser.parseResponse(soapXml);

        assertNotNull(result);
        assertEquals(678, result.cStat());
        assertEquals("Consumo Indevido", result.xMotivo());
        assertNull(result.chNFCom());
        assertNull(result.nProt());
    }

    @Test
    void populatesRawFields() {
        String soapXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap:Body>"
                + "<nfcomRecepcaoResult xmlns=\"http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao\">"
                + "<retNFCom xmlns=\"http://www.portalfiscal.inf.br/NFCom\" versao=\"1.00\">"
                + "<tpAmb>2</tpAmb>"
                + "<cUF>35</cUF>"
                + "<cStat>100</cStat>"
                + "<xMotivo>Sucesso</xMotivo>"
                + "</retNFCom>"
                + "</nfcomRecepcaoResult>"
                + "</soap:Body>"
                + "</soap:Envelope>";

        ParsedResponse result = parser.parseResponse(soapXml);

        assertNotNull(result.rawFields());
        assertFalse(result.rawFields().isEmpty());
        assertEquals("100", result.rawFields().get("cStat"));
        assertEquals("Sucesso", result.rawFields().get("xMotivo"));
        assertEquals("35", result.rawFields().get("cUF"));
        assertEquals("2", result.rawFields().get("tpAmb"));
    }

    @Test
    void throwsOnNullXml() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseResponse(null));
    }

    @Test
    void throwsOnEmptyXml() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseResponse(""));
    }
}
