package com.nfcom.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Represents all NFCom data fields needed to build the submission XML.
 * Follows the XSD structure with nested records for each group.
 *
 * @param ide       issuer identification data
 * @param emit      emitter (supplier) data
 * @param dest      destination (subscriber) data
 * @param assinante subscriber contract data
 * @param det       list of service items
 * @param total     invoice totals
 * @param gFat      billing/faturamento data
 * @param accessKey 44-digit NFCom access key (chave de acesso)
 */
public record NfcomData(
        @Valid @NotNull IdeData ide,
        @Valid @NotNull EmitData emit,
        @Valid @NotNull DestData dest,
        @Valid @NotNull AssinanteData assinante,
        @Valid @NotNull List<ItemData> det,
        @Valid @NotNull TotalData total,
        @Valid @NotNull GFatData gFat,
        @NotBlank String accessKey
) {

    // ---------------------------------------------------------------
    // Nested records
    // ---------------------------------------------------------------

    /**
     * {@code <ide>} — issuer identification data.
     */
    public record IdeData(
            int cUF,
            int tpAmb,
            String mod,
            int serie,
            long nNF,
            String cNF,
            String cDV,
            String dhEmi,
            int tpEmis,
            String nSiteAutoriz,
            int cMunFG,
            int finNFCom,
            int tpFat,
            String verProc
    ) {}

    /**
     * {@code <emit>} — emitter (supplier) data.
     */
    public record EmitData(
            String cnpj,
            String ie,
            String xNome,
            int crt
    ) {}

    /**
     * {@code <dest>} — destination (subscriber) data.
     */
    public record DestData(
            String xNome,
            String cnpj,
            String cpf,
            int indIEDest,
            String ie,
            EnderDest enderDest
    ) {

        /**
         * {@code <enderDest>} — destination address.
         */
        public record EnderDest(
                String logradouro,
                String nro,
                String xBairro,
                int cMun,
                String xMun,
                String uf,
                String cep,
                int cPais
        ) {}
    }

    /**
     * {@code <assinante>} — subscriber contract data.
     */
    public record AssinanteData(
            String iCodAssinante,
            int tpAssinante,
            int tpServUtil
    ) {}

    /**
     * {@code <det>} — a single service item.
     */
    public record ItemData(
            int nItem,
            String cProd,
            String xProd,
            String cClass,
            String CFOP,
            String uCom,
            BigDecimal qCom,
            BigDecimal vUnCom,
            BigDecimal vProd
    ) {}

    /**
     * {@code <total>} — invoice totals.
     */
    public record TotalData(
            BigDecimal vProd,
            BigDecimal vNF
    ) {}

    /**
     * {@code <gFat>} — billing/faturamento data.
     */
    public record GFatData(
            String competFat,
            String dVencFat,
            String dPerUsoIni,
            String dPerUsoFim,
            String codBarras
    ) {}
}
