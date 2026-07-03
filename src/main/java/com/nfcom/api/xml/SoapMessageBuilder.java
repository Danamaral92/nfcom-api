package com.nfcom.api.xml;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Builds SOAP 1.2 envelopes for SEFAZ NFCom web services.
 * <p>
 * Follows the NFCom specification:
 * <ul>
 *   <li>SOAP 1.2 envelope namespace: {@code http://www.w3.org/2003/05/soap-envelope}</li>
 *   <li>Uses {@code soap:} prefix for envelope elements</li>
 *   <li>Body content uses no namespace prefix (default namespace) per NFCom convention</li>
 *   <li>String-based building for predictable structure</li>
 * </ul>
 */
@ApplicationScoped
public class SoapMessageBuilder {

    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    /**
     * Builds a SOAP 1.2 envelope with the given body content.
     * The body content is placed raw inside the SOAP Body element.
     *
     * @param bodyContent      the XML content to place inside the SOAP Body
     * @param serviceNamespace the target namespace for the service (may be null)
     * @return the complete SOAP envelope as an XML string
     */
    public String buildSoapEnvelope(String bodyContent, String serviceNamespace) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_DECLARATION).append("\n");
        sb.append("<soap:Envelope xmlns:soap=\"").append(SOAP_NS).append("\">\n");
        sb.append("<soap:Header/>\n");
        sb.append("<soap:Body>\n");
        if (bodyContent != null) {
            sb.append(bodyContent).append("\n");
        }
        sb.append("</soap:Body>\n");
        sb.append("</soap:Envelope>");
        return sb.toString();
    }

    /**
     * Builds a SOAP 1.2 envelope for the NFComRecepcao service.
     * The body contains an {@code nfcomReq} element wrapping the GZip+Base64
     * compressed payload with the NFComRecepcao WSDL namespace.
     *
     * @param compressedBase64 the GZip-compressed and Base64-encoded NFCom XML
     * @return the complete SOAP envelope as an XML string
     */
    public String buildRecepcaoEnvelope(String compressedBase64) {
        String serviceNs = "http://www.portalfiscal.inf.br/NFCom/wsdl/NFComRecepcao";
        String bodyContent = "<nfcomReq xmlns=\"" + serviceNs + "\">"
                + compressedBase64
                + "</nfcomReq>";
        return buildSoapEnvelope(bodyContent, serviceNs);
    }

    /**
     * Builds a SOAP 1.2 envelope for standard (non-compressed) NFCom services.
     * The body contains an {@code nfeDadosMsg} element wrapping the raw XML
     * request with the service-specific WSDL namespace.
     *
     * @param xmlBody          the raw XML body content
     * @param serviceNamespace the target WSDL namespace for the service
     * @return the complete SOAP envelope as an XML string
     */
    public String buildStandardEnvelope(String xmlBody, String serviceNamespace) {
        String bodyContent = "<nfeDadosMsg xmlns=\"" + serviceNamespace + "\">\n"
                + xmlBody + "\n"
                + "</nfeDadosMsg>";
        return buildSoapEnvelope(bodyContent, serviceNamespace);
    }
}
