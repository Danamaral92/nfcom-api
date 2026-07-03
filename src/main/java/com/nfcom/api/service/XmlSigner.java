package com.nfcom.api.service;

import com.nfcom.api.shared.error.ErrorDetail;
import com.nfcom.api.shared.error.NfcomException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

/**
 * Applies XML Digital Signature (Enveloped, RSA-SHA1, C14N) to NFCom XML documents.
 * <p>
 * Uses the standard {@code javax.xml.crypto.dsig} package (JSR 105) from the JDK
 * with no external dependencies.
 * <p>
 * Per NFCom specification:
 * <ul>
 *   <li>Signature algorithm: RSA-SHA1 ({@code http://www.w3.org/2000/09/xmldsig#rsa-sha1})</li>
 *   <li>Digest algorithm: SHA-1 ({@code http://www.w3.org/2000/09/xmldsig#sha1})</li>
 *   <li>Canonicalization: C14N ({@code http://www.w3.org/TR/2001/REC-xml-c14n-20010315})</li>
 *   <li>Transforms: Enveloped signature + C14N (in that order)</li>
 *   <li>KeyInfo: X509Data containing only the X509Certificate (no SubjectName, IssuerSerial, SKI)</li>
 *   <li>No namespace prefix on Signature elements</li>
 * </ul>
 */
@ApplicationScoped
public class XmlSigner {

    private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NFCOM_NS = "http://www.portalfiscal.inf.br/NFCom";
    private static final String C14N_ALGORITHM = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";
    private static final String RSA_SHA1_ALGORITHM = "http://www.w3.org/2000/09/xmldsig#rsa-sha1";
    private static final String SHA1_ALGORITHM = "http://www.w3.org/2000/09/xmldsig#sha1";

    private final PrivateKey privateKey;
    private final X509Certificate certificate;

    /**
     * CDI constructor — injects {@link CertLoader} to obtain the private key and
     * X509 certificate for signing.
     *
     * @throws NfcomException if the certificate or private key is not loaded
     */
    @Inject
    public XmlSigner(CertLoader certLoader) {
        this.privateKey = certLoader.getPrivateKey();
        this.certificate = certLoader.getCertificate();
        if (this.privateKey == null || this.certificate == null) {
            throw new NfcomException("CERT_NOT_LOADED", 503,
                    "ICP-Brasil certificate not loaded. Set NFCOM_CERT_PATH and NFCOM_CERT_PASSWORD.",
                    List.of());
        }
    }

    /**
     * Constructor for testing — allows direct injection of key and certificate.
     */
    XmlSigner(PrivateKey privateKey, X509Certificate certificate) {
        this.privateKey = privateKey;
        this.certificate = certificate;
    }

    /**
     * Signs an NFCom XML document by finding the {@code infNFCom} element with an
     * {@code Id} attribute, creating an enveloped XML Digital Signature, and
     * inserting the {@code Signature} element as the last child of the root
     * {@code <NFCom>} element.
     * <p>
     * The {@code infNFCom} element must have an {@code Id} attribute with value
     * format {@code NFCom} + 44-digit access key (e.g.
     * {@code NFCom35200600012345000176550000000012345678901234}).
     *
     * @param xmlDoc the NFCom XML document to sign (modified in place)
     * @return the signed XML document (same instance as input)
     * @throws IllegalArgumentException if the document has no {@code infNFCom}
     *                                  element with an {@code Id} attribute
     * @throws Exception                if signing fails
     */
    public Document signNfcom(Document xmlDoc) throws Exception {
        Element root = xmlDoc.getDocumentElement();

        Element infNFCom = findInfNFComElement(root);
        if (infNFCom == null) {
            throw new IllegalArgumentException(
                    "Document must contain an <infNFCom> element with an Id attribute");
        }

        String idValue = infNFCom.getAttribute("Id");
        if (idValue == null || idValue.isBlank()) {
            throw new IllegalArgumentException(
                    "<infNFCom> element must have a non-empty Id attribute");
        }

        return signXml(xmlDoc, idValue);
    }

    /**
     * Signs any XML element identified by the given {@code idAttributeValue}.
     * <p>
     * The element with the matching {@code Id} attribute value must exist in the
     * document. The {@code Signature} element is inserted as the last child of
     * the document root element.
     *
     * @param xmlDoc            the XML document to sign (modified in place)
     * @param idAttributeValue  the value of the {@code Id} attribute on the element
     *                          to reference (without the {@code #} prefix — will be
     *                          added automatically)
     * @return the signed XML document (same instance as input)
     * @throws Exception if signing fails or the referenced element cannot be found
     */
    public Document signXml(Document xmlDoc, String idAttributeValue) throws Exception {
        // Register the Id attribute using DOM's id mechanism so that the signature
        // implementation can resolve the reference via getElementById.
        registerIdAttribute(xmlDoc, idAttributeValue);

        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");

        // Create digest method (SHA-1)
        DigestMethod digestMethod = fac.newDigestMethod(SHA1_ALGORITHM, null);

        // Create transforms: Enveloped → C14N (in that order, per NFCom spec)
        List<Transform> transforms = List.of(
                fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                fac.newTransform(CanonicalizationMethod.INCLUSIVE,
                        (C14NMethodParameterSpec) null)
        );

        // Create Reference pointing to the element by its Id
        Reference ref = fac.newReference(
                "#" + idAttributeValue,
                digestMethod,
                transforms,
                null,
                null
        );

        // Create SignedInfo with C14N canonicalization and RSA-SHA1 signature method
        SignedInfo signedInfo = fac.newSignedInfo(
                fac.newCanonicalizationMethod(
                        CanonicalizationMethod.INCLUSIVE,
                        (C14NMethodParameterSpec) null
                ),
                fac.newSignatureMethod(RSA_SHA1_ALGORITHM, null),
                Collections.singletonList(ref)
        );

        // Create KeyInfo with X509Data containing only the X509Certificate
        KeyInfoFactory kif = fac.getKeyInfoFactory();
        KeyInfo keyInfo = kif.newKeyInfo(
                Collections.singletonList(
                        kif.newX509Data(
                                Collections.singletonList(certificate)
                        )
                )
        );

        // Sign — signature is added as the last child of the root element
        Element root = xmlDoc.getDocumentElement();
        DOMSignContext dsc = new DOMSignContext(privateKey, root);

        // Set the default namespace prefix to empty string so that XMLDSIG
        // elements use the default namespace (no "ds:" prefix) as required by
        // the NFCom specification.
        dsc.putNamespacePrefix(XMLDSIG_NS, "");

        fac.newXMLSignature(signedInfo, keyInfo).sign(dsc);

        return xmlDoc;
    }

    /**
     * Serializes a DOM {@link Document} to its XML string representation.
     * <p>
     * Uses no indentation to ensure C14N consistency — indentation would introduce
     * whitespace text nodes that alter the DOM tree and break signature validation
     * after a parse round-trip.
     *
     * @param doc the document to serialize
     * @return the XML string
     */
    public String documentToString(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "no");
            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize document to string", e);
        }
    }

    /**
     * Parses an XML string into a DOM {@link Document}.
     *
     * @param xml the XML string to parse
     * @return the parsed document
     */
    public Document parseToDocument(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XML string", e);
        }
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * Finds the {@code <infNFCom>} element within the document that has an
     * {@code Id} attribute.
     */
    private Element findInfNFComElement(Element root) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if ("infNFCom".equals(el.getLocalName())
                        && el.hasAttribute("Id")) {
                    return el;
                }
            }
        }
        return null;
    }

    /**
     * Registers the ID attribute on the element that matches the given Id value
     * so that {@link Document#getElementById} can resolve it.
     * <p>
     * Searches all elements in the document for one with an {@code Id} attribute
     * matching the given value, then calls {@code setIdAttribute("Id", true)} on it.
     */
    private void registerIdAttribute(Document doc, String idAttributeValue) {
        NodeList allElements = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element el = (Element) allElements.item(i);
            String idVal = el.getAttribute("Id");
            if (idAttributeValue.equals(idVal)) {
                el.setIdAttribute("Id", true);
                return;
            }
        }
    }
}
