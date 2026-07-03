package com.nfcom.api.shared.error;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorModelsTest {

    // --- ApiResponse tests ---

    @Test
    void successResponseHasStatusSuccessAndData() {
        ApiResponse<String> response = ApiResponse.success("some data");

        assertEquals("success", response.getStatus());
        assertEquals("some data", response.getData());
        assertNull(response.getError());
    }

    @Test
    void errorResponseHasStatusErrorAndErrorObject() {
        ApiResponse<String> response = ApiResponse.error("ERR_CODE", "Something went wrong", null);

        assertEquals("error", response.getStatus());
        assertNull(response.getData());
        assertNotNull(response.getError());
        assertEquals("ERR_CODE", response.getError().getCode());
        assertEquals("Something went wrong", response.getError().getMessage());
    }

    @Test
    void errorResponseWithDetails() {
        List<ErrorDetail> details = List.of(
                new ErrorDetail("field1", "must not be null"),
                new ErrorDetail("field2", "must be 44 digits")
        );
        ApiResponse<String> response = ApiResponse.error("VALIDATION_ERROR", "Invalid request", details);

        assertEquals("error", response.getStatus());
        assertEquals("VALIDATION_ERROR", response.getError().getCode());
        assertEquals("Invalid request", response.getError().getMessage());
        assertEquals(2, response.getError().getDetails().size());
        assertEquals("field1", response.getError().getDetails().get(0).getField());
        assertEquals("must be 44 digits", response.getError().getDetails().get(1).getMessage());
    }

    @Test
    void successCanHoldNullData() {
        ApiResponse<String> response = ApiResponse.success(null);
        assertEquals("success", response.getStatus());
        assertNull(response.getData());
    }

    @Test
    void errorFromApiErrorFactory() {
        ApiError error = new ApiError("NOT_FOUND", "Resource not found", null);
        ApiResponse<String> response = ApiResponse.error(error);

        assertEquals("error", response.getStatus());
        assertSame(error, response.getError());
    }

    // --- ApiError tests ---

    @Test
    void apiErrorHasCodeMessageAndDetails() {
        List<ErrorDetail> details = List.of(new ErrorDetail("key", "invalid"));
        ApiError error = new ApiError("BAD_REQUEST", "Bad request", details);

        assertEquals("BAD_REQUEST", error.getCode());
        assertEquals("Bad request", error.getMessage());
        assertEquals(1, error.getDetails().size());
    }

    @Test
    void apiErrorCanHaveNullDetails() {
        ApiError error = new ApiError("ERROR", "message", null);
        assertNull(error.getDetails());
    }

    // --- ErrorDetail tests ---

    @Test
    void errorDetailHasFieldAndMessage() {
        ErrorDetail detail = new ErrorDetail("accessKey", "must be exactly 44 digits");

        assertEquals("accessKey", detail.getField());
        assertEquals("must be exactly 44 digits", detail.getMessage());
    }

    // --- NfcomException tests ---

    @Test
    void nfcomExceptionHasCodeAndHttpStatus() {
        NfcomException ex = new NfcomException("SEFAZ_ERROR", 502, "Service unavailable");

        assertEquals("SEFAZ_ERROR", ex.getCode());
        assertEquals(502, ex.getHttpStatus());
        assertEquals("Service unavailable", ex.getMessage());
        assertNull(ex.getDetails());
    }

    @Test
    void nfcomExceptionWithDetails() {
        List<ErrorDetail> details = List.of(
                new ErrorDetail(null, "Taxa de requisições excedida para este serviço. Aguarde 1 hora.")
        );
        NfcomException ex = new NfcomException("RATE_LIMIT_EXCEEDED", 429, "cStat 678 — Consumo Indevido", details);

        assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(429, ex.getHttpStatus());
        assertEquals(1, ex.getDetails().size());
        assertEquals("Taxa de requisições excedida para este serviço. Aguarde 1 hora.",
                ex.getDetails().get(0).getMessage());
    }

    @Test
    void nfcomExceptionIsRuntimeException() {
        NfcomException ex = new NfcomException("ERR", 500, "error");
        assertInstanceOf(RuntimeException.class, ex);
    }
}
