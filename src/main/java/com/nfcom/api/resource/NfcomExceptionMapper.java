package com.nfcom.api.resource;

import com.nfcom.api.shared.error.ApiResponse;
import com.nfcom.api.shared.error.NfcomException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NfcomExceptionMapper implements ExceptionMapper<NfcomException> {

    @Override
    public Response toResponse(NfcomException exception) {
        ApiResponse<Void> body = ApiResponse.error(
                exception.getCode(),
                exception.getMessage(),
                exception.getDetails()
        );
        return Response.status(exception.getHttpStatus())
                .entity(body)
                .type("application/json")
                .build();
    }
}
