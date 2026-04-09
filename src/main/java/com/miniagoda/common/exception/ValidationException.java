package com.miniagoda.common.exception;

public class ValidationException extends RuntimeException {

    public ValidationException() {
        super("The request contains invalid or missing fields.");
    }

    public ValidationException(String message) {
        super(message);
    }
}