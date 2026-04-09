package com.miniagoda.common.exception;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Authentication is required to access this resource.");
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}