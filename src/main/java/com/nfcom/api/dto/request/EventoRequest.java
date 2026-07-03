package com.nfcom.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;

/**
 * REST request DTO for registering an NFCom event.
 * <p>
 * Converts to an {@link EventoData} record for processing by
 * {@link com.nfcom.api.service.SefazService}.
 * Supports all 8 NFCom event types: 110111, 240140, 240150, 240151,
 * 240160, 240161, 240162, 240170.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventoRequest {

    @NotBlank(message = "tpEvento must not be null or empty")
    @Pattern(regexp = "\\d{6}", message = "tpEvento must be a 6-digit event type code")
    private String tpEvento;

    @NotBlank(message = "chNFCom must not be null or empty")
    @Pattern(regexp = "\\d{44}", message = "chNFCom must be exactly 44 digits")
    private String chNFCom;

    @Positive(message = "nSeqEvento must be positive")
    private int nSeqEvento = 1;

    private String descEvento;

    private String nProt;

    private String xJust;

    /** Environment: 1=production, 2=testing (default). */
    private int tpAmb = 2;

    /** Emitter CNPJ (14 digits). Optional for some event types. */
    private String cnpj;

    /** SEFAZ organ code (default 35=SP). */
    private int cOrgao = 35;

    /** Event datetime in ISO-8601 format. Auto-filled if not set. */
    private String dhEvento;

    // ---------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------

    public EventoRequest() {
    }

    // ---------------------------------------------------------------
    // Conversion to EventoData
    // ---------------------------------------------------------------

    /**
     * Converts this REST request DTO to the internal {@link EventoData} record.
     *
     * @return a new EventoData with fields mapped from this request
     */
    public EventoData toEventoData() {
        return new EventoData(
                tpEvento,
                chNFCom,
                nSeqEvento,
                descEvento != null ? descEvento : "",
                nProt,
                xJust,
                tpAmb,
                cnpj != null ? cnpj : "",
                cOrgao,
                dhEvento != null ? dhEvento : OffsetDateTime.now().toString()
        );
    }

    // ---------------------------------------------------------------
    // Getters and setters
    // ---------------------------------------------------------------

    public String getTpEvento() {
        return tpEvento;
    }

    public void setTpEvento(String tpEvento) {
        this.tpEvento = tpEvento;
    }

    public String getChNFCom() {
        return chNFCom;
    }

    public void setChNFCom(String chNFCom) {
        this.chNFCom = chNFCom;
    }

    public int getNSeqEvento() {
        return nSeqEvento;
    }

    public void setNSeqEvento(int nSeqEvento) {
        this.nSeqEvento = nSeqEvento;
    }

    public String getDescEvento() {
        return descEvento;
    }

    public void setDescEvento(String descEvento) {
        this.descEvento = descEvento;
    }

    public String getNProt() {
        return nProt;
    }

    public void setNProt(String nProt) {
        this.nProt = nProt;
    }

    public String getXJust() {
        return xJust;
    }

    public void setXJust(String xJust) {
        this.xJust = xJust;
    }

    public int getTpAmb() {
        return tpAmb;
    }

    public void setTpAmb(int tpAmb) {
        this.tpAmb = tpAmb;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public int getCOrgao() {
        return cOrgao;
    }

    public void setCOrgao(int cOrgao) {
        this.cOrgao = cOrgao;
    }

    public String getDhEvento() {
        return dhEvento;
    }

    public void setDhEvento(String dhEvento) {
        this.dhEvento = dhEvento;
    }
}
