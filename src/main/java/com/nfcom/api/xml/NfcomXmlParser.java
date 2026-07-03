package com.nfcom.api.xml;

import jakarta.enterprise.context.ApplicationScoped;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses SEFAZ SOAP response XML and extracts NFCom response fields.
 * <p>
 * Handles all 5 SEFAZ NFCom services by navigating the SOAP envelope,
 * finding the service-specific result wrapper, and extracting the NFCom
 * response element's field values by local tag name.
 */
@ApplicationScoped
public class NfcomXmlParser {

    private static final String NFCOM_NS = "http://www.portalfiscal.inf.br/NFCom";

    private final DocumentBuilder docBuilder;

    public NfcomXmlParser() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            this.docBuilder = factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize NfcomXmlParser", e);
        }
    }

    /**
     * Parses a SEFAZ SOAP response XML and extracts the response fields.
     *
     * @param soapResponseXml the complete SOAP response XML string
     * @return the parsed response data
     * @throws IllegalArgumentException if the XML is null or empty
     */
    public ParsedResponse parseResponse(String soapResponseXml) {
        if (soapResponseXml == null || soapResponseXml.isBlank()) {
            throw new IllegalArgumentException("SOAP response XML must not be null or empty");
        }

        try {
            InputSource is = new InputSource(new StringReader(soapResponseXml));
            Document doc = docBuilder.parse(is);

            // Find the NFCom response element — the first child element of the
            // service-specific result wrapper inside SOAP Body that belongs to
            // the NFCom namespace (http://www.portalfiscal.inf.br/NFCom).
            Element responseElement = findNfcomResponseElement(doc);
            if (responseElement == null) {
                throw new RuntimeException("Could not find NFCom response element in SOAP response");
            }

            // Extract known fields and raw fields
            Map<String, String> rawFields = extractAllFields(responseElement);

            int cStat = parseInt(rawFields.get("cStat"));
            String xMotivo = rawFields.get("xMotivo");
            String chNFCom = rawFields.get("chNFCom");
            String nProt = rawFields.get("nProt");
            String dhRecbto = rawFields.get("dhRecbto");

            return new ParsedResponse(cStat, xMotivo, chNFCom, nProt, dhRecbto, rawFields);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SOAP response XML", e);
        }
    }

    // -----------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------

    /**
     * Walks the DOM tree to find the NFCom response element.
     * The path is: soap:Envelope → soap:Body → result-wrapper → nfcom-response-element
     * The NFCom response element is identified by its namespace
     * {@code http://www.portalfiscal.inf.br/NFCom}.
     */
    private Element findNfcomResponseElement(Document doc) {
        // Get the soap:Body element
        Element envelope = doc.getDocumentElement();
        Element body = findChildByLocalName(envelope, "Body");
        if (body == null) return null;

        // Get the first child element (result wrapper, e.g. nfcomRecepcaoResult)
        Element resultWrapper = getFirstChildElement(body);
        if (resultWrapper == null) return null;

        // Get the first child element in the NFCom namespace
        return findChildInNamespace(resultWrapper, NFCOM_NS);
    }

    /**
     * Finds a child element by its local tag name (ignoring namespace).
     */
    private Element findChildByLocalName(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if (el.getLocalName().equals(localName)) {
                    return el;
                }
            }
        }
        return null;
    }

    /**
     * Returns the first child element of the given parent.
     */
    private Element getFirstChildElement(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) child;
            }
        }
        return null;
    }

    /**
     * Finds the first child element belonging to the given namespace.
     */
    private Element findChildInNamespace(Element parent, String namespace) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if (namespace.equals(el.getNamespaceURI())) {
                    return el;
                }
            }
        }
        return null;
    }

    /**
     * Extracts all text content of child elements as a map of local name → value.
     * Only includes elements in the NFCom namespace.
     */
    private Map<String, String> extractAllFields(Element responseElement) {
        Map<String, String> fields = new HashMap<>();
        NodeList children = responseElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                String localName = el.getLocalName();
                String textContent = el.getTextContent();
                if (localName != null && textContent != null) {
                    fields.put(localName, textContent.trim());
                }
            }
        }
        return fields;
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The result of parsing a SEFAZ SOAP response.
     *
     * @param cStat     SEFAZ status code
     * @param xMotivo   SEFAZ status description
     * @param chNFCom   NFCom access key (44 digits), may be null
     * @param nProt     protocol number, may be null
     * @param dhRecbto  reception timestamp, may be null
     * @param rawFields all extracted fields as a map for extensibility
     */
    public record ParsedResponse(
            int cStat,
            String xMotivo,
            String chNFCom,
            String nProt,
            String dhRecbto,
            Map<String, String> rawFields
    ) {}
}
