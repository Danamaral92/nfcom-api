package com.nfcom.api.dto.request;

/**
 * Represents data needed to build a SEFAZ NFCom event XML.
 * <p>
 * Supports cancellation (tpEvento=110111) and other event types.
 * Use static factory methods for common event types.
 *
 * @param tpEvento    event type code (e.g. "110111" for cancellation)
 * @param chNFCom     NFCom access key (44 digits)
 * @param nSeqEvento  event sequence number
 * @param descEvento  event description
 * @param nProt       protocol number (required for cancellation)
 * @param xJust       justification text (required for cancellation)
 * @param tpAmb       environment type (1=production, 2=testing)
 * @param cnpj        emitter CNPJ
 * @param cOrgao      SEFAZ organ code
 * @param dhEvento    event datetime (ISO 8601)
 */
public record EventoData(
        String tpEvento,
        String chNFCom,
        int nSeqEvento,
        String descEvento,
        String nProt,
        String xJust,
        int tpAmb,
        String cnpj,
        int cOrgao,
        String dhEvento
) {

    /**
     * Creates a cancellation event (tpEvento=110111).
     *
     * @param chNFCom    the NFCom access key to cancel
     * @param nProt      the protocol number from the original authorization
     * @param xJust      justification for cancellation (min 15 chars)
     * @param tpAmb      environment type
     * @param cnpj       emitter CNPJ
     * @param cOrgao     SEFAZ organ code
     * @param dhEvento   event datetime
     * @return a new EventoData configured for cancellation
     */
    public static EventoData cancelamento(
            String chNFCom, String nProt, String xJust,
            int tpAmb, String cnpj, int cOrgao, String dhEvento) {
        return new EventoData(
                "110111",
                chNFCom,
                1,
                "Cancelamento",
                nProt,
                xJust,
                tpAmb,
                cnpj,
                cOrgao,
                dhEvento
        );
    }
}
