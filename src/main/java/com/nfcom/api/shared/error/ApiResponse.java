package com.nfcom.api.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String status;
    private T data;
    private ApiError error;

    public ApiResponse() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ApiError getError() {
        return error;
    }

    public void setError(ApiError error) {
        this.error = error;
    }

    // --- Static factories ---

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "success";
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> error(String code, String message, List<ErrorDetail> details) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "error";
        response.error = new ApiError(code, message, details);
        return response;
    }

    public static <T> ApiResponse<T> error(ApiError apiError) {
        ApiResponse<T> response = new ApiResponse<>();
        response.status = "error";
        response.error = apiError;
        return response;
    }
}
