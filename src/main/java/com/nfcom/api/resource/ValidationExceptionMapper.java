package com.nfcom.api.resource;

import com.nfcom.api.shared.error.ApiResponse;
import com.nfcom.api.shared.error.ErrorDetail;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ErrorDetail> details = exception.getConstraintViolations().stream()
                .map(this::toErrorDetail)
                .toList();

        ApiResponse<Void> body = ApiResponse.error(
                "VALIDATION_ERROR",
                "Invalid request",
                details
        );
        return Response.status(422)
                .entity(body)
                .type("application/json")
                .build();
    }

    private ErrorDetail toErrorDetail(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath() != null
                ? violation.getPropertyPath().toString()
                : null;
        return new ErrorDetail(field, violation.getMessage());
    }
}
