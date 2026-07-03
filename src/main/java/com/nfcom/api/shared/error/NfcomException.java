package com.nfcom.api.shared.error;

import java.util.List;

/**
 * Base exception for NFCom API errors.
 * Carries a SEFAZ cStat code, an HTTP status mapping, and optional detail list.
 */
public class NfcomException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final List<ErrorDetail> details;

    public NfcomException(String code, int httpStatus, String message) {
        this(code, httpStatus, message, null);
    }

    public NfcomException(String code, int httpStatus, String message, List<ErrorDetail> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
