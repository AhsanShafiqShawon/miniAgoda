package com.miniagoda.common.response;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, Instant.now(), null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, error, message, path, Instant.now(), fieldErrors);
    }

}