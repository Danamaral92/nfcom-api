package com.nfcom.api.resource;

import com.nfcom.api.shared.error.ApiResponse;
import com.nfcom.api.shared.error.ErrorDetail;
import com.nfcom.api.shared.error.NfcomException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionMapperTest {

    private final NfcomExceptionMapper nfcomMapper = new NfcomExceptionMapper();

    @Test
    void mapsNfcomExceptionToCorrectHttpStatus() {
        NfcomException ex = new NfcomException("SEFAZ_ERROR", 502, "Bad gateway");
        Response response = nfcomMapper.toResponse(ex);

        assertEquals(502, response.getStatus());
        assertInstanceOf(ApiResponse.class, response.getEntity());

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getEntity();
        assertEquals("error", apiResponse.getStatus());
        assertEquals("SEFAZ_ERROR", apiResponse.getError().getCode());
        assertEquals("Bad gateway", apiResponse.getError().getMessage());
    }

    @Test
    void mapsNfcomExceptionWithDetails() {
        List<ErrorDetail> details = List.of(
                new ErrorDetail(null, "Taxa de requisições excedida")
        );
        NfcomException ex = new NfcomException("RATE_LIMIT_EXCEEDED", 429, "cStat 678 — Consumo Indevido", details);
        Response response = nfcomMapper.toResponse(ex);

        assertEquals(429, response.getStatus());

        ApiResponse<?> apiResponse = (ApiResponse<?>) response.getEntity();
        assertEquals(1, apiResponse.getError().getDetails().size());
        assertEquals("Taxa de requisições excedida", apiResponse.getError().getDetails().get(0).getMessage());
    }
}
