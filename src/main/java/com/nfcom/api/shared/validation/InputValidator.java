package com.nfcom.api.shared.validation;

import com.nfcom.api.shared.error.ErrorDetail;
import com.nfcom.api.shared.error.NfcomException;

import java.util.List;
import java.util.Set;

public final class InputValidator {

    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of(
            "110111", "240140", "240150", "240151",
            "240160", "240161", "240162", "240170"
    );

    private InputValidator() {
        // utility class
    }

    /**
     * Validates that the given access key is exactly 44 digits.
     *
     * @throws NfcomException with HTTP 422 if validation fails
     */
    public static void validateAccessKey(String key) {
        if (key == null || !key.matches("\\d{44}")) {
            String message = key == null || key.isEmpty()
                    ? "accessKey must not be null or empty"
                    : "accessKey must be exactly 44 digits";
            throw new NfcomException("VALIDATION_ERROR", 422, message,
                    List.of(new ErrorDetail("accessKey", message)));
        }
    }

    /**
     * Validates that the given CNPJ is exactly 14 digits.
     *
     * @throws NfcomException with HTTP 422 if validation fails
     */
    public static void validateCnpj(String cnpj) {
        if (cnpj == null || !cnpj.matches("\\d{14}")) {
            String message = cnpj == null || cnpj.isEmpty()
                    ? "cnpj must not be null or empty"
                    : "cnpj must be exactly 14 digits";
            throw new NfcomException("VALIDATION_ERROR", 422, message,
                    List.of(new ErrorDetail("cnpj", message)));
        }
    }

    /**
     * Validates that the given event type is one of the allowed values.
     *
     * @throws NfcomException with HTTP 422 if validation fails
     */
    public static void validateEventType(String type) {
        if (type == null || !ALLOWED_EVENT_TYPES.contains(type)) {
            String message = type == null
                    ? "eventType must not be null"
                    : "eventType must be one of: " + String.join(", ", ALLOWED_EVENT_TYPES);
            throw new NfcomException("VALIDATION_ERROR", 422, message,
                    List.of(new ErrorDetail("eventType", message)));
        }
    }
}
