package com.nfcom.api.resource;

import com.nfcom.api.dto.request.EventoData;
import com.nfcom.api.dto.request.EventoRequest;
import com.nfcom.api.dto.request.NfcomData;
import com.nfcom.api.service.SefazService;
import com.nfcom.api.shared.error.ApiResponse;
import com.nfcom.api.shared.validation.InputValidator;
import com.nfcom.api.xml.NfcomXmlParser.ParsedResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST endpoints for the NFCom API — wraps SEFAZ NFCom (Model 62) web services.
 * <p>
 * All endpoints return JSON responses wrapped in the standard
 * {@link ApiResponse} envelope. Input validation errors return HTTP 422
 * with field-level error details. SEFAZ errors are mapped to
 * appropriate HTTP status codes (502, 504, 429, etc.).
 */
@Path("/api/v1/nfcom")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "NFCom", description = "NFCom API — REST wrapper for SEFAZ NFCom web services")
public class NfcomResource {

    @Inject
    SefazService sefazService;

    // ---------------------------------------------------------------
    // T10: GET /api/v1/nfcom/status  —  Query SEFAZ service status
    // ---------------------------------------------------------------

    @GET
    @Path("/status")
    @Operation(summary = "Query SEFAZ NFCom service status",
            description = "Returns the current operational status of the SEFAZ NFCom service. "
                    + "cStat=107 means operational, 108 partial, 109 down.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Service status retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @APIResponse(responseCode = "502", description = "SEFAZ connection error"),
            @APIResponse(responseCode = "504", description = "SEFAZ timeout")
    })
    public Response getStatus(
            @QueryParam("tpAmb") @DefaultValue("2") int tpAmb) {
        ParsedResponse result = sefazService.consultarStatus(tpAmb);
        return Response.ok(ApiResponse.success(result)).build();
    }

    // ---------------------------------------------------------------
    // T11: GET /api/v1/nfcom/{accessKey}  —  Query NFCom by access key
    // ---------------------------------------------------------------

    @GET
    @Path("/{accessKey}")
    @Operation(summary = "Query NFCom by 44-digit access key",
            description = "Queries the SEFAZ NFCom consulta service for the status of a "
                    + "previously submitted NFCom using its 44-digit access key.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "NFCom status retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @APIResponse(responseCode = "422", description = "Invalid access key (must be 44 digits)"),
            @APIResponse(responseCode = "502", description = "SEFAZ connection error")
    })
    public Response consultarNfcom(
            @PathParam("accessKey") String accessKey,
            @QueryParam("tpAmb") @DefaultValue("2") int tpAmb) {
        InputValidator.validateAccessKey(accessKey);
        ParsedResponse result = sefazService.consultarNfcom(accessKey, tpAmb);
        return Response.ok(ApiResponse.success(result)).build();
    }

    // ---------------------------------------------------------------
    // T12: POST /api/v1/nfcom/submit  —  Submit NFCom invoice
    // ---------------------------------------------------------------

    @POST
    @Path("/submit")
    @Operation(summary = "Submit NFCom invoice to SEFAZ",
            description = "Submits a complete NFCom invoice to SEFAZ. The request JSON is "
                    + "converted to XML, digitally signed, GZip+Base64 compressed, and sent "
                    + "to SEFAZ via SOAP over mTLS.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "NFCom submitted successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @APIResponse(responseCode = "422", description = "Validation error"),
            @APIResponse(responseCode = "502", description = "SEFAZ error"),
            @APIResponse(responseCode = "504", description = "SEFAZ timeout")
    })
    public Response submitNfcom(@Valid NfcomData data) {
        ParsedResponse result = sefazService.submitNfcom(data);
        return Response.ok(ApiResponse.success(result)).build();
    }

    // ---------------------------------------------------------------
    // T13a: POST /api/v1/nfcom/events  —  Register NFCom event
    // ---------------------------------------------------------------

    @POST
    @Path("/events")
    @Operation(summary = "Register NFCom event",
            description = "Registers an NFCom event (cancellation, fiscal events, etc.) "
                    + "with SEFAZ. Supports all 8 event types: 110111, 240140, 240150, "
                    + "240151, 240160, 240161, 240162, 240170.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Event registered successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @APIResponse(responseCode = "422", description = "Validation error"),
            @APIResponse(responseCode = "502", description = "SEFAZ error")
    })
    public Response enviarEvento(@Valid EventoRequest eventRequest) {
        InputValidator.validateEventType(eventRequest.getTpEvento());
        EventoData eventoData = eventRequest.toEventoData();
        ParsedResponse result = sefazService.enviarEvento(eventoData);
        return Response.ok(ApiResponse.success(result)).build();
    }

    // ---------------------------------------------------------------
    // T13b: GET /api/v1/nfcom/taxpayer/{cnpj}  —  Query taxpayer
    // ---------------------------------------------------------------

    @GET
    @Path("/taxpayer/{cnpj}")
    @Operation(summary = "Query taxpayer registration",
            description = "Queries SEFAZ for taxpayer registration status by CNPJ.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Taxpayer registration retrieved",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @APIResponse(responseCode = "422", description = "Invalid CNPJ (must be 14 digits)"),
            @APIResponse(responseCode = "502", description = "SEFAZ connection error")
    })
    public Response consultarCadastro(
            @PathParam("cnpj") String cnpj,
            @QueryParam("tpAmb") @DefaultValue("2") int tpAmb) {
        InputValidator.validateCnpj(cnpj);
        ParsedResponse result = sefazService.consultarCadastro(cnpj, tpAmb);
        return Response.ok(ApiResponse.success(result)).build();
    }
}
