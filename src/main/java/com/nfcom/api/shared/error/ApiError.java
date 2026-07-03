package com.nfcom.api.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private String code;
    private String message;
    private List<ErrorDetail> details;

    public ApiError() {
    }

    public ApiError(String code, String message, List<ErrorDetail> details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }

    public void setDetails(List<ErrorDetail> details) {
        this.details = details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiError apiError = (ApiError) o;
        return Objects.equals(code, apiError.code)
                && Objects.equals(message, apiError.message)
                && Objects.equals(details, apiError.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, details);
    }
}
