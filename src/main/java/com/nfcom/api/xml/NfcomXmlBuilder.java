package com.nfcom.api.xml;

import com.nfcom.api.dto.request.EventoData;
import com.nfcom.api.dto.request.NfcomData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigDecimal;
import java.util.List;

/**
 * Builds NFCom XML request documents using the DOM API.
 * <p>
 * This is a plain utility class (not &#64;ApplicationScoped) that creates
 * DOM {@link Document} instances for each SEFAZ NFCom service.
 * <p>
 * All built documents use the NFCom namespace
 * {@code http://www.portalfiscal.inf.br/NFCom}.
 */
public class NfcomXmlBuilder {

    private static final String NFCOM_NS = "http://www.portalfiscal.inf.br/NFCom";

    private final DocumentBuilder docBuilder;

    public NfcomXmlBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            this.docBuilder = factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NfcomXmlBuilder", e);
        }
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Builds the full NFCom submission XML document.
     *
     * @param data the complete NFCom data
     * @return the DOM document
     */
    public Document buildNfcomXml(NfcomData data) {
        Document doc = docBuilder.newDocument();

        Element root = doc.createElementNS(NFCOM_NS, "NFCom");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", NFCOM_NS);
        doc.appendChild(root);

        Element infNFCom = doc.createElementNS(NFCOM_NS, "infNFCom");
        infNFCom.setAttribute("Id", "NFCom" + data.accessKey());
        infNFCom.setAttribute("versao", "1.00");
        root.appendChild(infNFCom);

        buildIde(doc, infNFCom, data.ide());
        buildEmit(doc, infNFCom, data.emit());
        buildDest(doc, infNFCom, data.dest());
        buildAssinante(doc, infNFCom, data.assinante());
        buildDetList(doc, infNFCom, data.det());
        buildTotal(doc, infNFCom, data.total());
        buildGFat(doc, infNFCom, data.gFat());

        return doc;
    }

    /**
     * Builds a consultation XML document.
     *
     * @param accessKey the 44-digit NFCom access key
     * @param tpAmb     environment type
     * @return the DOM document
     */
    public Document buildConsultXml(String accessKey, int tpAmb) {
        Document doc = docBuilder.newDocument();

        Element root = doc.createElementNS(NFCOM_NS, "consSitNFCom");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", NFCOM_NS);
        root.setAttribute("versao", "1.00");
        doc.appendChild(root);

        addChild(doc, root, "tpAmb", String.valueOf(tpAmb));
        addChild(doc, root, "xServ", "CONSULTAR");
        addChild(doc, root, "chNFCom", accessKey);

        return doc;
    }

    /**
     * Builds a status service XML document.
     *
     * @param tpAmb environment type
     * @return the DOM document
     */
    public Document buildStatusXml(int tpAmb) {
        Document doc = docBuilder.newDocument();

        Element root = doc.createElementNS(NFCOM_NS, "consStatServNFCom");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", NFCOM_NS);
        root.setAttribute("versao", "1.00");
        doc.appendChild(root);

        addChild(doc, root, "tpAmb", String.valueOf(tpAmb));
        addChild(doc, root, "xServ", "STATUS");

        return doc;
    }

    /**
     * Builds an event XML document.
     *
     * @param eventData the event data
     * @return the DOM document
     */
    public Document buildEventXml(EventoData eventData) {
        Document doc = docBuilder.newDocument();

        Element root = doc.createElementNS(NFCOM_NS, "eventoNFCom");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", NFCOM_NS);
        root.setAttribute("versao", "1.00");
        doc.appendChild(root);

        String idValue = "ID" + eventData.tpEvento() + eventData.chNFCom() + eventData.nSeqEvento();

        Element infEvento = doc.createElementNS(NFCOM_NS, "infEvento");
        infEvento.setAttribute("Id", idValue);
        root.appendChild(infEvento);

        addChild(doc, infEvento, "cOrgao", String.valueOf(eventData.cOrgao()));
        addChild(doc, infEvento, "tpAmb", String.valueOf(eventData.tpAmb()));
        addChild(doc, infEvento, "CNPJ", eventData.cnpj());
        addChild(doc, infEvento, "chNFCom", eventData.chNFCom());
        addChild(doc, infEvento, "dhEvento", eventData.dhEvento());
        addChild(doc, infEvento, "tpEvento", eventData.tpEvento());
        addChild(doc, infEvento, "nSeqEvento", String.valueOf(eventData.nSeqEvento()));

        // detEvento — details depend on event type
        Element detEvento = doc.createElementNS(NFCOM_NS, "detEvento");
        detEvento.setAttribute("versaoEvento", "1.00");
        infEvento.appendChild(detEvento);

        if ("110111".equals(eventData.tpEvento())) {
            // Cancellation
            Element evCanc = doc.createElementNS(NFCOM_NS, "evCancNFCom");
            detEvento.appendChild(evCanc);
            addChild(doc, evCanc, "descEvento", eventData.descEvento());
            addChild(doc, evCanc, "nProt", eventData.nProt());
            addChild(doc, evCanc, "xJust", eventData.xJust());
        } else {
            // Generic event: add descEvento directly in detEvento
            addChild(doc, detEvento, "descEvento", eventData.descEvento());
        }

        return doc;
    }

    /**
     * Builds a taxpayer registration consultation XML document.
     *
     * @param cnpj  the CNPJ to consult
     * @param tpAmb environment type
     * @return the DOM document
     */
    public Document buildCadastroXml(String cnpj, int tpAmb) {
        Document doc = docBuilder.newDocument();

        Element root = doc.createElementNS(NFCOM_NS, "consultaCadastro");
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", NFCOM_NS);
        root.setAttribute("versao", "1.00");
        doc.appendChild(root);

        addChild(doc, root, "tpAmb", String.valueOf(tpAmb));
        addChild(doc, root, "xServ", "CONSULTAR");
        addChild(doc, root, "CNPJ", cnpj);

        return doc;
    }

    // ---------------------------------------------------------------
    // Section builders
    // ---------------------------------------------------------------

    private void buildIde(Document doc, Element parent, NfcomData.IdeData ide) {
        if (ide == null) return;
        Element el = doc.createElementNS(NFCOM_NS, "ide");
        parent.appendChild(el);

        addChild(doc, el, "cUF", String.valueOf(ide.cUF()));
        addChild(doc, el, "tpAmb", String.valueOf(ide.tpAmb()));
        if (ide.mod() != null) addChild(doc, el, "mod", ide.mod());
        addChild(doc, el, "serie", String.valueOf(ide.serie()));
        addChild(doc, el, "nNF", String.valueOf(ide.nNF()));
        if (ide.cNF() != null) addChild(doc, el, "cNF", ide.cNF());
        if (ide.cDV() != null) addChild(doc, el, "cDV", ide.cDV());
        if (ide.dhEmi() != null) addChild(doc, el, "dhEmi", ide.dhEmi());
        addChild(doc, el, "tpEmis", String.valueOf(ide.tpEmis()));
        if (ide.nSiteAutoriz() != null) addChild(doc, el, "nSiteAutoriz", ide.nSiteAutoriz());
        addChild(doc, el, "cMunFG", String.valueOf(ide.cMunFG()));
        addChild(doc, el, "finNFCom", String.valueOf(ide.finNFCom()));
        addChild(doc, el, "tpFat", String.valueOf(ide.tpFat()));
        if (ide.verProc() != null) addChild(doc, el, "verProc", ide.verProc());
    }

    private void buildEmit(Document doc, Element parent, NfcomData.EmitData emit) {
        if (emit == null) return;
        Element el = doc.createElementNS(NFCOM_NS, "emit");
        parent.appendChild(el);

        if (emit.cnpj() != null) addChild(doc, el, "CNPJ", emit.cnpj());
        if (emit.ie() != null) addChild(doc, el, "IE", emit.ie());
        if (emit.xNome() != null) addChild(doc, el, "xNome", emit.xNome());
        addChild(doc, el, "CRT", String.valueOf(emit.crt()));
    }

    private void buildDest(Document doc, Element parent, NfcomData.DestData dest) {
        if (dest == null) return;
        Element el = doc.createElementNS(NFCOM_NS, "dest");
        parent.appendChild(el);

        if (dest.xNome() != null) addChild(doc, el, "xNome", dest.xNome());
        if (dest.cnpj() != null) addChild(doc, el, "CNPJ", dest.cnpj());
        if (dest.cpf() != null) addChild(doc, el, "CPF", dest.cpf());
        addChild(doc, el, "indIEDest", String.valueOf(dest.indIEDest()));
        if (dest.ie() != null) addChild(doc, el, "IE", dest.ie());
        if (dest.enderDest() != null) {
            buildEnderDest(doc, el, dest.enderDest());
        }
    }

    private void buildEnderDest(Document doc, Element parent, NfcomData.DestData.EnderDest ender) {
        Element el = doc.createElementNS(NFCOM_NS, "enderDest");
        parent.appendChild(el);

        if (ender.logradouro() != null) addChild(doc, el, "xLgr", ender.logradouro());
        if (ender.nro() != null) addChild(doc, el, "nro", ender.nro());
        if (ender.xBairro() != null) addChild(doc, el, "xBairro", ender.xBairro());
        addChild(doc, el, "cMun", String.valueOf(ender.cMun()));
        if (ender.xMun() != null) addChild(doc, el, "xMun", ender.xMun());
        if (ender.uf() != null) addChild(doc, el, "UF", ender.uf());
        if (ender.cep() != null) addChild(doc, el, "CEP", ender.cep());
        addChild(doc, el, "cPais", String.valueOf(ender.cPais()));
    }

    private void buildAssinante(Document doc, Element parent, NfcomData.AssinanteData assinante) {
        if (assinante == null) return;
        Element el = doc.createElementNS(NFCOM_NS, "assinante");
        parent.appendChild(el);

        if (assinante.iCodAssinante() != null) addChild(doc, el, "iCodAssinante", assinante.iCodAssinante());
        addChild(doc, el, "tpAssinante", String.valueOf(assinante.tpAssinante()));
        addChild(doc, el, "tpServUtil", String.valueOf(assinante.tpServUtil()));
    }

    private void buildDetList(Document doc, Element parent, List<NfcomData.ItemData> items) {
        if (items == null) return;
        for (NfcomData.ItemData item : items) {
            if (item == null) continue;
            Element el = doc.createElementNS(NFCOM_NS, "det");
            el.setAttribute("nItem", String.valueOf(item.nItem()));
            parent.appendChild(el);

            if (item.cProd() != null) addChild(doc, el, "cProd", item.cProd());
            if (item.xProd() != null) addChild(doc, el, "xProd", item.xProd());
            if (item.cClass() != null) addChild(doc, el, "cClass", item.cClass());
            if (item.CFOP() != null) addChild(doc, el, "CFOP", item.CFOP());
            if (item.uCom() != null) addChild(doc, el, "uCom", item.uCom());
            if (item.qCom() != null) addChild(doc, el, "qCom", formatBigDecimal(item.qCom()));
            if (item.vUnCom() != null) addChild(doc, el, "vUnCom", formatBigDecimal(item.vUnCom()));
            if (item.vProd() != null) addChild(doc, el, "vProd", formatBigDecimal(item.vProd()));
        }
    }

    private void buildTotal(Document doc, Element parent, NfcomData.TotalData total) {
        if (total == null) return;
        Element el = doc.createElementNS(NFCOM_NS, "total");
        parent.appendChild(el);

        if (total.vProd() != null) addChild(doc, el, "vProd", formatBigDecimal(total.vProd()));
        if (total.vNF() != null) addChild(doc, el, "vNF", formatBigDecimal(total.vNF()));
    }

    private void buildGFat(Document doc, Element parent, NfcomData.GFatData gFat) {
        if (gFat == null) return;
        Element el = doc.createElementNS(NFCOM_NS, "gFat");
        parent.appendChild(el);

        if (gFat.competFat() != null) addChild(doc, el, "competFat", gFat.competFat());
        if (gFat.dVencFat() != null) addChild(doc, el, "dVencFat", gFat.dVencFat());
        if (gFat.dPerUsoIni() != null) addChild(doc, el, "dPerUsoIni", gFat.dPerUsoIni());
        if (gFat.dPerUsoFim() != null) addChild(doc, el, "dPerUsoFim", gFat.dPerUsoFim());
        if (gFat.codBarras() != null) addChild(doc, el, "codBarras", gFat.codBarras());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static void addChild(Document doc, Element parent, String tagName, String textContent) {
        Element child = doc.createElementNS(NFCOM_NS, tagName);
        child.setTextContent(textContent);
        parent.appendChild(child);
    }

    /**
     * Formats a BigDecimal value as a string without trailing zeros.
     * Uses the plain string representation to avoid scientific notation.
     */
    private static String formatBigDecimal(BigDecimal value) {
        if (value == null) return null;
        return value.stripTrailingZeros().toPlainString();
    }
}
