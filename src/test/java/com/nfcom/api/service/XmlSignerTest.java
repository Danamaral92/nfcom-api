package com.nfcom.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class XmlSignerTest {

    private static final String TEST_CERT_PATH = "src/test/resources/test-cert.p12";
    private static final String TEST_CERT_PASSWORD = "changeit";
    private static final String ACCESS_KEY = "35200600012345000176550000000012345678901234";
    private static final String NFCOM_ID = "NFCom" + ACCESS_KEY;
    private static final String NFCOM_NS = "http://www.portalfiscal.inf.br/NFCom";
    private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String C14N_ALGORITHM = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";

    private XmlSigner signer;
    private X509Certificate cert;

    @BeforeEach
    void setUp() {
        CertLoader loader = new CertLoader(TEST_CERT_PATH, TEST_CERT_PASSWORD, null);
        loader.init();
        cert = loader.getCertificate();
        signer = new XmlSigner(loader);
    }

    // ---------------------------------------------------------------
    // Basic structure tests
    // ---------------------------------------------------------------

    @Test
    void signatureIsEnveloped() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element root = signed.getDocumentElement();
        assertEquals("NFCom", root.getLocalName());
        assertEquals(NFCOM_NS, root.getNamespaceURI());

        // Signature should be the last element child of root
        Element lastChildElement = getLastChildElement(root);
        assertNotNull(lastChildElement, "Root should have an element child after signing");
        assertEquals("Signature", lastChildElement.getLocalName());
        assertEquals(XMLDSIG_NS, lastChildElement.getNamespaceURI());
    }

    @Test
    void signatureHasNoNamespacePrefix() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element signature = findSignatureElement(signed);
        assertNotNull(signature);
        String prefix = signature.getPrefix();
        assertTrue(prefix == null || prefix.isEmpty(),
                "Signature element should have no namespace prefix, but had: '" + prefix + "'");

        // Check all descendant elements of Signature also have no prefix
        assertNoPrefixOnSignatureDescendants(signature);
    }

    @Test
    void referenceUriMatchesAccessKey() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element reference = findElementDescendant(signed, XMLDSIG_NS, "Reference");
        assertNotNull(reference, "Reference element should exist");

        String uri = reference.getAttribute("URI");
        assertEquals("#" + NFCOM_ID, uri,
                "Reference URI should be #NFCom<accessKey>");
    }

    @Test
    void canonicalizationIsC14N() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element c14nMethod = findElementDescendant(signed, XMLDSIG_NS, "CanonicalizationMethod");
        assertNotNull(c14nMethod);
        String algorithm = c14nMethod.getAttribute("Algorithm");
        assertEquals(C14N_ALGORITHM, algorithm,
                "Canonicalization should be C14N (not exclusive)");
    }

    @Test
    void signatureAlgorithmIsRsaSha1() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element sigMethod = findElementDescendant(signed, XMLDSIG_NS, "SignatureMethod");
        assertNotNull(sigMethod);
        String algorithm = sigMethod.getAttribute("Algorithm");
        assertEquals("http://www.w3.org/2000/09/xmldsig#rsa-sha1", algorithm,
                "Signature algorithm should be RSA-SHA1");
    }

    @Test
    void digestAlgorithmIsSha1() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element digestMethod = findElementDescendant(signed, XMLDSIG_NS, "DigestMethod");
        assertNotNull(digestMethod);
        String algorithm = digestMethod.getAttribute("Algorithm");
        assertEquals("http://www.w3.org/2000/09/xmldsig#sha1", algorithm,
                "Digest algorithm should be SHA-1");
    }

    // ---------------------------------------------------------------
    // Transform tests
    // ---------------------------------------------------------------

    @Test
    void transformsAreEnvelopedAndC14N() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element reference = findElementDescendant(signed, XMLDSIG_NS, "Reference");
        assertNotNull(reference);

        NodeList transformsList = reference.getElementsByTagNameNS(XMLDSIG_NS, "Transform");
        assertEquals(2, transformsList.getLength(),
                "Should have exactly 2 transforms");

        String firstTransformAlg = ((Element) transformsList.item(0)).getAttribute("Algorithm");
        String secondTransformAlg = ((Element) transformsList.item(1)).getAttribute("Algorithm");

        assertEquals("http://www.w3.org/2000/09/xmldsig#enveloped-signature",
                firstTransformAlg, "First transform must be Enveloped signature");
        assertEquals(C14N_ALGORITHM, secondTransformAlg,
                "Second transform must be C14N");
    }

    // ---------------------------------------------------------------
    // KeyInfo tests
    // ---------------------------------------------------------------

    @Test
    void keyInfoContainsOnlyX509Certificate() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element keyInfo = findElementDescendant(signed, XMLDSIG_NS, "KeyInfo");
        assertNotNull(keyInfo);

        Element x509Data = findFirstChildElement(keyInfo, XMLDSIG_NS, "X509Data");
        assertNotNull(x509Data, "KeyInfo should contain X509Data");

        // Should contain X509Certificate
        NodeList certNodes = x509Data.getElementsByTagNameNS(XMLDSIG_NS, "X509Certificate");
        assertEquals(1, certNodes.getLength(),
                "X509Data should contain exactly one X509Certificate");

        // Should NOT contain other X509Data elements
        NodeList subjectNames = x509Data.getElementsByTagNameNS(XMLDSIG_NS, "X509SubjectName");
        assertEquals(0, subjectNames.getLength(),
                "X509Data should NOT contain X509SubjectName");

        NodeList issuerSerials = x509Data.getElementsByTagNameNS(XMLDSIG_NS, "X509IssuerSerial");
        assertEquals(0, issuerSerials.getLength(),
                "X509Data should NOT contain X509IssuerSerial");

        NodeList ski = x509Data.getElementsByTagNameNS(XMLDSIG_NS, "X509SKI");
        assertEquals(0, ski.getLength(), "X509Data should NOT contain X509SKI");
    }

    // ---------------------------------------------------------------
    // Signature validation tests
    // ---------------------------------------------------------------

    @Test
    void signatureVerifiesWithPublicKey() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        assertTrue(verifySignature(signed, cert.getPublicKey()),
                "Signature should verify with the certificate's public key");
    }

    @Test
    void signatureVerifiesWithEmbeddedCertificate() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        // Extract certificate from signed XML and use it for verification
        Element x509CertElement = findElementDescendant(signed, XMLDSIG_NS, "X509Certificate");
        assertNotNull(x509CertElement);
        // Remove whitespace/newlines that DOM insertTextContent may add
        String base64Cert = x509CertElement.getTextContent().replaceAll("\\s+", "");

        // Decode and build certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        byte[] certBytes = Base64.getDecoder().decode(base64Cert);
        X509Certificate embeddedCert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));

        assertTrue(verifySignature(signed, embeddedCert.getPublicKey()),
                "Signature should verify with the embedded X509Certificate");
    }

    @Test
    void signXmlWithExplicitId() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signXml(doc, NFCOM_ID);

        Element signature = findSignatureElement(signed);
        assertNotNull(signature, "Signature should be present after signXml");

        assertTrue(verifySignature(signed, cert.getPublicKey()),
                "Signature created via signXml should verify");
    }

    @Test
    void signatureValueIsPresentAndNonEmpty() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);

        Element sigValue = findElementDescendant(signed, XMLDSIG_NS, "SignatureValue");
        assertNotNull(sigValue);
        String value = sigValue.getTextContent();
        assertNotNull(value);
        assertFalse(value.isBlank(), "SignatureValue should not be empty");
    }

    // ---------------------------------------------------------------
    // Utility method tests
    // ---------------------------------------------------------------

    @Test
    void documentToStringProducesValidXml() throws Exception {
        Document doc = createMinimalNfcomDocument();
        String xml = signer.documentToString(doc);
        assertNotNull(xml);
        assertTrue(xml.contains("NFCom"), "XML should contain NFCom element");
        assertTrue(xml.contains(NFCOM_ID), "XML should contain the NFCom ID");
    }

    @Test
    void parseToDocumentProducesValidDocument() throws Exception {
        Document doc = createMinimalNfcomDocument();
        String xml = signer.documentToString(doc);
        Document parsed = signer.parseToDocument(xml);

        assertNotNull(parsed);
        Element root = parsed.getDocumentElement();
        assertEquals("NFCom", root.getLocalName());
        assertEquals(NFCOM_NS, root.getNamespaceURI());
    }

    @Test
    void stringRoundTripPreservesContent() throws Exception {
        Document doc = createMinimalNfcomDocument();
        String xml = signer.documentToString(doc);

        // Remove whitespace for comparison
        String normalized = xml.replaceAll(">\\s+<", "><").trim();
        assertTrue(normalized.contains("35200600012345000176550000000012345678901234"),
                "Round-tripped XML should preserve the access key");
    }

    @Test
    void signedDocumentRoundTripPreservesSignature() throws Exception {
        Document doc = createMinimalNfcomDocument();
        Document signed = signer.signNfcom(doc);
        String xml = signer.documentToString(signed);
        Document reparsed = signer.parseToDocument(xml);

        assertTrue(verifySignature(reparsed, cert.getPublicKey()),
                "Signature should survive string→document round trip");
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    void throwsExceptionForMissingIdAttribute() {
        Document doc = createDocumentWithoutId();
        assertThrows(IllegalArgumentException.class, () -> signer.signNfcom(doc),
                "Should throw IllegalArgumentException when infNFCom has no Id attribute");
    }

    @Test
    void signXmlThrowsForNonExistentReference() throws Exception {
        Document doc = createMinimalNfcomDocument();
        assertThrows(Exception.class, () -> signer.signXml(doc, "NonExistentId"),
                "Should throw when referenced ID does not exist");
    }

    // ---------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------

    private Document createMinimalNfcomDocument() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        Element root = doc.createElementNS(NFCOM_NS, "NFCom");
        doc.appendChild(root);

        Element infNFCom = doc.createElementNS(NFCOM_NS, "infNFCom");
        infNFCom.setAttribute("Id", NFCOM_ID);
        infNFCom.setAttribute("versao", "1.00");
        infNFCom.setIdAttribute("Id", true);
        root.appendChild(infNFCom);

        Element ide = doc.createElementNS(NFCOM_NS, "ide");
        infNFCom.appendChild(ide);

        addChild(doc, ide, "cUF", "35");
        addChild(doc, ide, "cNF", "1234567");
        addChild(doc, ide, "nNF", "123456789");
        addChild(doc, ide, "dhEmi", "2026-07-03T14:30:00-03:00");
        addChild(doc, ide, "tpAmb", "2");
        addChild(doc, ide, "verProc", "1.0.0");

        Element emit = doc.createElementNS(NFCOM_NS, "emit");
        infNFCom.appendChild(emit);
        addChild(doc, emit, "CNPJ", "00000000000191");
        addChild(doc, emit, "IE", "123456789");

        Element dest = doc.createElementNS(NFCOM_NS, "dest");
        infNFCom.appendChild(dest);
        addChild(doc, dest, "CNPJ", "00000000000192");
        addChild(doc, dest, "xNome", "NFCom Test Subscriber");
        addChild(doc, dest, "indIEDest", "9");

        return doc;
    }

    private Document createDocumentWithoutId() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element root = doc.createElementNS(NFCOM_NS, "NFCom");
            doc.appendChild(root);

            Element infNFCom = doc.createElementNS(NFCOM_NS, "infNFCom");
            infNFCom.setAttribute("versao", "1.00");
            root.appendChild(infNFCom);

            return doc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addChild(Document doc, Element parent, String tagName, String textContent) {
        Element child = doc.createElementNS(NFCOM_NS, tagName);
        child.setTextContent(textContent);
        parent.appendChild(child);
    }

    private Element getLastChildElement(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return (Element) node;
            }
        }
        return null;
    }

    private Element findSignatureElement(Document doc) {
        return findElementDescendant(doc, XMLDSIG_NS, "Signature");
    }

    private Element findElementDescendant(Document doc, String ns, String localName) {
        NodeList nl = doc.getElementsByTagNameNS(ns, localName);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private Element findElementDescendant(Element parent, String ns, String localName) {
        NodeList nl = parent.getElementsByTagNameNS(ns, localName);
        return nl.getLength() > 0 ? (Element) nl.item(0) : null;
    }

    private Element findFirstChildElement(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if (localName.equals(el.getLocalName())
                        && (ns == null || ns.equals(el.getNamespaceURI()))) {
                    return el;
                }
            }
        }
        return null;
    }

    /**
     * Verifies an XML Signature using the given public key.
     * <p>
     * Disables secure validation because NFCom uses RSA-SHA1 which is
     * considered weak by JDK 17+ defaults.
     */
    private boolean verifySignature(Document doc, java.security.PublicKey publicKey) throws Exception {
        Element signatureElement = findSignatureElement(doc);
        if (signatureElement == null) {
            return false;
        }

        // Ensure the Id attribute is registered for DOM getElementById resolution.
        // When parsing from string, the parser doesn't know "Id" is an ID attribute,
        // so we must re-register it for signature validation to work.
        registerIdAttributes(doc);

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        DOMValidateContext valContext = new DOMValidateContext(publicKey, signatureElement);

        // NFCom requires RSA-SHA1; disable secure validation so JDK allows it
        valContext.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);

        XMLSignature signature = fac.unmarshalXMLSignature(valContext);
        return signature.validate(valContext);
    }

    /**
     * Registers all {@code Id} attributes as DOM ID attributes so that
     * {@link Document#getElementById} can resolve them.
     */
    private void registerIdAttributes(Document doc) {
        NodeList allElements = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element el = (Element) allElements.item(i);
            if (el.hasAttribute("Id") && !el.getAttribute("Id").isBlank()) {
                el.setIdAttribute("Id", true);
            }
        }
    }

    /**
     * Recursively checks that all signature descendant elements have no prefix.
     */
    private void assertNoPrefixOnSignatureDescendants(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                String prefix = el.getPrefix();
                assertTrue(prefix == null || prefix.isEmpty(),
                        "Element '" + el.getLocalName() + "' should have no prefix, but had: '" + prefix + "'");
                assertNoPrefixOnSignatureDescendants(el);
            }
        }
    }
}
