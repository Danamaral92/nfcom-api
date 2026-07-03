package com.nfcom.api.resource;

import com.nfcom.api.dto.request.EventoRequest;
import com.nfcom.api.service.SefazService;
import com.nfcom.api.xml.NfcomXmlParser.ParsedResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NfcomResource} — NFCom REST API endpoints.
 * <p>
 * Uses {@code @QuarkusTest} with a mocked {@link SefazService} to verify
 * endpoint behaviour, validation, response envelope structure, and error
 * handling without requiring real SEFAZ connectivity or certificates.
 */
@QuarkusTest
class NfcomResourceTest {

    private static final String VALID_ACCESS_KEY = "35200600012345000176550000000012345678901234";
    private static final String VALID_CNPJ = "00000000000191";
    private static final int TP_AMB = 2;

    @InjectMock
    SefazService sefazService;

    private ParsedResponse successResponse;

    @BeforeEach
    void setUp() {
        successResponse = new ParsedResponse(
                100,
                "Autorizado o Uso da NFCom",
                VALID_ACCESS_KEY,
                "135200000123456",
                "2026-07-03T14:30:00-03:00",
                Map.of(
                        "cStat", "100",
                        "xMotivo", "Autorizado o Uso da NFCom",
                        "chNFCom", VALID_ACCESS_KEY,
                        "nProt", "135200000123456",
                        "dhRecbto", "2026-07-03T14:30:00-03:00"
                )
        );
    }

    // ---------------------------------------------------------------
    // T10: GET /api/v1/nfcom/status
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/nfcom/status")
    class StatusEndpoint {

        @Test
        @DisplayName("200 — returns service status")
        void getStatusReturns200() {
            when(sefazService.consultarStatus(TP_AMB)).thenReturn(successResponse);

            given()
                    .queryParam("tpAmb", TP_AMB)
                    .when()
                    .get("/api/v1/nfcom/status")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("success"))
                    .body("data", notNullValue())
                    .body("data.cStat", equalTo(100))
                    .body("data.xMotivo", equalTo("Autorizado o Uso da NFCom"));
        }

        @Test
        @DisplayName("200 — defaults tpAmb to 2")
        void getStatusDefaultsTpAmb() {
            when(sefazService.consultarStatus(TP_AMB)).thenReturn(successResponse);

            given()
                    .when()
                    .get("/api/v1/nfcom/status")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("success"));
        }
    }

    // ---------------------------------------------------------------
    // T11: GET /api/v1/nfcom/{accessKey}
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/nfcom/{accessKey}")
    class ConsultaEndpoint {

        @Test
        @DisplayName("200 — returns NFCom status for valid access key")
        void consultarNfcomWithValidKeyReturns200() {
            when(sefazService.consultarNfcom(VALID_ACCESS_KEY, TP_AMB)).thenReturn(successResponse);

            given()
                    .pathParam("accessKey", VALID_ACCESS_KEY)
                    .queryParam("tpAmb", TP_AMB)
                    .when()
                    .get("/api/v1/nfcom/{accessKey}")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("success"))
                    .body("data.chNFCom", equalTo(VALID_ACCESS_KEY));
        }

        @Test
        @DisplayName("422 — invalid access key (too short)")
        void consultarNfcomWithInvalidKeyReturns422() {
            given()
                    .pathParam("accessKey", "12345")
                    .when()
                    .get("/api/v1/nfcom/{accessKey}")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo("error"))
                    .body("error.code", equalTo("VALIDATION_ERROR"))
                    .body("error.details[0].field", equalTo("accessKey"));
        }

        @Test
        @DisplayName("422 — non-digit access key")
        void consultarNfcomWithNonDigitKeyReturns422() {
            given()
                    .pathParam("accessKey", "ABCD1234567890123456789012345678901234567890")
                    .when()
                    .get("/api/v1/nfcom/{accessKey}")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo("error"))
                    .body("error.code", equalTo("VALIDATION_ERROR"))
                    .body("error.details[0].field", equalTo("accessKey"));
        }
    }

    // ---------------------------------------------------------------
    // T12: POST /api/v1/nfcom/submit
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/nfcom/submit")
    class SubmitEndpoint {

        @Test
        @DisplayName("200 — submits NFCom successfully")
        void submitNfcomReturns200() {
            when(sefazService.submitNfcom(any())).thenReturn(successResponse);

            // Minimal valid NfcomData — enough to pass JSON deserialization
            String body = """
                    {
                        "accessKey": "35200600012345000176550000000012345678901234",
                        "ide": {
                            "cUF": 35, "tpAmb": 2, "mod": "62", "serie": 1,
                            "nNF": 123456, "cNF": "ABCD1234", "cDV": "7",
                            "dhEmi": "2026-07-03T14:30:00-03:00", "tpEmis": 1,
                            "nSiteAutoriz": "1", "cMunFG": 3550308,
                            "finNFCom": 1, "tpFat": 1, "verProc": "1.0.0"
                        },
                        "emit": { "cnpj": "00000000000191", "ie": "123456789",
                                  "xNome": "Emitter", "crt": 1 },
                        "dest": { "xNome": "Destinatario", "cnpj": "00000000000192",
                                  "cpf": null, "indIEDest": 9, "ie": null,
                                  "enderDest": { "logradouro": "Rua A", "nro": "100",
                                      "xBairro": "Centro", "cMun": 3550308,
                                      "xMun": "Sao Paulo", "uf": "SP",
                                      "cep": "01001000", "cPais": 1058 }
                        },
                        "assinante": { "iCodAssinante": "123456", "tpAssinante": 1,
                                       "tpServUtil": 1 },
                        "det": [{ "nItem": 1, "cProd": "PROD001", "xProd": "Servico",
                                  "cClass": "010101", "CFOP": "5303", "uCom": "UN",
                                  "qCom": 1.00, "vUnCom": 100.00, "vProd": 100.00 }],
                        "total": { "vProd": 100.00, "vNF": 100.00 },
                        "gFat": { "competFat": "202607", "dVencFat": "2026-08-15",
                                  "dPerUsoIni": "2026-07-01", "dPerUsoFim": "2026-07-31",
                                  "codBarras": "1234567890ABCDEF" }
                    }
                    """;

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post("/api/v1/nfcom/submit")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("success"))
                    .body("data.cStat", equalTo(100));
        }
    }

    // ---------------------------------------------------------------
    // T13a: POST /api/v1/nfcom/events
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/nfcom/events")
    class EventsEndpoint {

        @Test
        @DisplayName("200 — registers event successfully")
        void enviarEventoReturns200() {
            when(sefazService.enviarEvento(any())).thenReturn(successResponse);

            String body = """
                    {
                        "tpEvento": "110111",
                        "chNFCom": "35200600012345000176550000000012345678901234",
                        "nSeqEvento": 1,
                        "descEvento": "Cancelamento",
                        "nProt": "135200000123456",
                        "xJust": "Cancelamento por erro no valor"
                    }
                    """;

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post("/api/v1/nfcom/events")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("success"))
                    .body("data.cStat", equalTo(100));
        }

        @Test
        @DisplayName("422 — missing tpEvento")
        void enviarEventoWithMissingTypeReturns422() {
            String body = """
                    {
                        "chNFCom": "35200600012345000176550000000012345678901234",
                        "nSeqEvento": 1
                    }
                    """;

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post("/api/v1/nfcom/events")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo("error"))
                    .body("error.code", equalTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("422 — invalid chNFCom")
        void enviarEventoWithInvalidKeyReturns422() {
            String body = """
                    {
                        "tpEvento": "110111",
                        "chNFCom": "123",
                        "nSeqEvento": 1
                    }
                    """;

            given()
                    .contentType(ContentType.JSON)
                    .body(body)
                    .when()
                    .post("/api/v1/nfcom/events")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo("error"))
                    .body("error.code", equalTo("VALIDATION_ERROR"))
                    .body("error.details", not(empty()));
        }
    }

    // ---------------------------------------------------------------
    // T13b: GET /api/v1/nfcom/taxpayer/{cnpj}
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/nfcom/taxpayer/{cnpj}")
    class TaxpayerEndpoint {

        @Test
        @DisplayName("200 — returns taxpayer data for valid CNPJ")
        void consultarCadastroWithValidCnpjReturns200() {
            when(sefazService.consultarCadastro(VALID_CNPJ, TP_AMB)).thenReturn(successResponse);

            given()
                    .pathParam("cnpj", VALID_CNPJ)
                    .queryParam("tpAmb", TP_AMB)
                    .when()
                    .get("/api/v1/nfcom/taxpayer/{cnpj}")
                    .then()
                    .statusCode(200)
                    .body("status", equalTo("success"))
                    .body("data.cStat", equalTo(100));
        }

        @Test
        @DisplayName("422 — invalid CNPJ (too short)")
        void consultarCadastroWithInvalidCnpjReturns422() {
            given()
                    .pathParam("cnpj", "12345")
                    .when()
                    .get("/api/v1/nfcom/taxpayer/{cnpj}")
                    .then()
                    .statusCode(422)
                    .body("status", equalTo("error"))
                    .body("error.code", equalTo("VALIDATION_ERROR"))
                    .body("error.details[0].field", equalTo("cnpj"));
        }
    }

    // ---------------------------------------------------------------
    // OpenAPI / Swagger UI
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("OpenAPI documentation")
    class OpenApiEndpoint {

        @Test
        @DisplayName("200 — OpenAPI spec is available")
        void openApiSpecIsAvailable() {
            given()
                    .when()
                    .get("/q/openapi")
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("200 — OpenAPI spec contains NFCom endpoints")
        void openApiSpecContainsNfcomEndpoints() {
            given()
                    .accept("application/json")
                    .when()
                    .get("/q/openapi")
                    .then()
                    .statusCode(200)
                    .body("paths", hasKey("/api/v1/nfcom/status"))
                    .body("paths", hasKey("/api/v1/nfcom/submit"))
                    .body("paths", hasKey("/api/v1/nfcom/events"))
                    .body("paths", hasKey("/api/v1/nfcom/taxpayer/{cnpj}"));
        }
    }
}
