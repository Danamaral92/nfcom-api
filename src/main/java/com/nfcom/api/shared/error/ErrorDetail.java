package com.nfcom.api.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetail {

    private String field;
    private String message;

    public ErrorDetail() {
    }

    public ErrorDetail(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ErrorDetail that = (ErrorDetail) o;
        return Objects.equals(field, that.field) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, message);
    }
}
