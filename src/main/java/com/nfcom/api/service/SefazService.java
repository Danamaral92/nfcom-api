package com.nfcom.api.service;

import com.nfcom.api.client.SefazClient;
import com.nfcom.api.dto.request.EventoData;
import com.nfcom.api.dto.request.NfcomData;
import com.nfcom.api.xml.GZipCompressor;
import com.nfcom.api.xml.NfcomXmlBuilder;
import com.nfcom.api.xml.NfcomXmlParser;
import com.nfcom.api.xml.NfcomXmlParser.ParsedResponse;
import com.nfcom.api.xml.SoapMessageBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.w3c.dom.Document;

/**
 * Orchestration layer for SEFAZ NFCom web services.
 * <p>
 * Wires together XML building, signing, compression, SOAP messaging,
 * HTTP transport, and response parsing into a single cohesive interface.
 * <p>
 * Each public method represents one of the 5 SEFAZ NFCom services.
 */
@ApplicationScoped
public class SefazService {

    private final CertLoader certLoader;
    private final XmlSigner xmlSigner;
    private final SefazClient sefazClient;
    private final SoapMessageBuilder soapMessageBuilder;
    private final NfcomXmlParser xmlParser;
    private final RateLimitBackoff rateLimitBackoff;

    /**
     * CDI constructor — all dependencies are injected.
     */
    @Inject
    public SefazService(CertLoader certLoader, XmlSigner xmlSigner,
                        SefazClient sefazClient, SoapMessageBuilder soapMessageBuilder,
                        NfcomXmlParser xmlParser, RateLimitBackoff rateLimitBackoff) {
        this.certLoader = certLoader;
        this.xmlSigner = xmlSigner;
        this.sefazClient = sefazClient;
        this.soapMessageBuilder = soapMessageBuilder;
        this.xmlParser = xmlParser;
        this.rateLimitBackoff = rateLimitBackoff;
    }

    // ---------------------------------------------------------------
    // Public API — 5 SEFAZ NFCom services
    // ---------------------------------------------------------------

    /**
     * Submits an NFCom for authorization.
     * <p>
     * Pipeline: build XML → sign → serialize → GZip+Base64 compress →
     * wrap in SOAP → send to SEFAZ → parse response.
     *
     * @param data the complete NFCom data
     * @return the parsed SEFAZ response
     */
    public ParsedResponse submitNfcom(NfcomData data) {
        NfcomXmlBuilder builder = new NfcomXmlBuilder();

        // 1. Build NFCom XML
        Document doc = builder.buildNfcomXml(data);

        // 2. Sign the document (XmlSigner finds infNFCom's Id attribute)
        try {
            xmlSigner.signNfcom(doc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign NFCom XML", e);
        }

        // 3. Serialize to string
        String xml = xmlSigner.documentToString(doc);

        // 4. Compress to GZip+Base64
        String compressed = GZipCompressor.compressToBase64(xml);

        // 5. Send via SefazClient (builds SOAP envelope internally)
        String soapResponse = sefazClient.submitNfcom(compressed);

        // 6. Parse response
        return xmlParser.parseResponse(soapResponse);
    }

    /**
     * Consults the status of a previously submitted NFCom.
     *
     * @param accessKey the 44-digit NFCom access key
     * @param tpAmb     environment type (1=production, 2=testing)
     * @return the parsed SEFAZ response
     */
    public ParsedResponse consultarNfcom(String accessKey, int tpAmb) {
        NfcomXmlBuilder builder = new NfcomXmlBuilder();

        // 1. Build consult XML
        Document doc = builder.buildConsultXml(accessKey, tpAmb);

        // 2. Serialize to string
        String xml = xmlSigner.documentToString(doc);

        // 3. Send via SefazClient
        String soapResponse = sefazClient.consultarNfcom(xml);

        // 4. Parse response
        return xmlParser.parseResponse(soapResponse);
    }

    /**
     * Consults the SEFAZ service status.
     *
     * @param tpAmb environment type (1=production, 2=testing)
     * @return the parsed SEFAZ response
     */
    public ParsedResponse consultarStatus(int tpAmb) {
        NfcomXmlBuilder builder = new NfcomXmlBuilder();

        // 1. Build status XML
        Document doc = builder.buildStatusXml(tpAmb);

        // 2. Serialize to string
        String xml = xmlSigner.documentToString(doc);

        // 3. Send via SefazClient
        String soapResponse = sefazClient.consultarStatus(xml);

        // 4. Parse response
        return xmlParser.parseResponse(soapResponse);
    }

    /**
     * Sends an event (e.g. cancellation) to SEFAZ.
     * <p>
     * Pipeline: build XML → sign → serialize → wrap in SOAP → send → parse.
     *
     * @param eventData the event data
     * @return the parsed SEFAZ response
     */
    public ParsedResponse enviarEvento(EventoData eventData) {
        NfcomXmlBuilder builder = new NfcomXmlBuilder();

        // 1. Build event XML
        Document doc = builder.buildEventXml(eventData);

        // 2. Sign the document — event Id is "ID{tpEvento}{chNFCom}{nSeqEvento}"
        String idValue = "ID" + eventData.tpEvento() + eventData.chNFCom() + eventData.nSeqEvento();
        try {
            xmlSigner.signXml(doc, idValue);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign event XML", e);
        }

        // 3. Serialize to string
        String xml = xmlSigner.documentToString(doc);

        // 4. Send via SefazClient
        String soapResponse = sefazClient.enviarEvento(xml);

        // 5. Parse response
        return xmlParser.parseResponse(soapResponse);
    }

    /**
     * Consults a taxpayer registration (CNPJ) at SEFAZ.
     *
     * @param cnpj  the CNPJ to consult
     * @param tpAmb environment type (1=production, 2=testing)
     * @return the parsed SEFAZ response
     */
    public ParsedResponse consultarCadastro(String cnpj, int tpAmb) {
        NfcomXmlBuilder builder = new NfcomXmlBuilder();

        // 1. Build cadastro XML
        Document doc = builder.buildCadastroXml(cnpj, tpAmb);

        // 2. Serialize to string
        String xml = xmlSigner.documentToString(doc);

        // 3. Send via SefazClient
        String soapResponse = sefazClient.consultarCadastro(xml);

        // 4. Parse response
        return xmlParser.parseResponse(soapResponse);
    }
}
