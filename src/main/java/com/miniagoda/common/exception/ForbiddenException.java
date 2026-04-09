package com.miniagoda.common.exception;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("You do not have permission to perform this action.");
    }

    public ForbiddenException(String message) {
        super(message);
    }
}