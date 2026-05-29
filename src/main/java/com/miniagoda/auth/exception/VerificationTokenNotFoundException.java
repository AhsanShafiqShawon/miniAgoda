package com.miniagoda.auth.exception;

public class VerificationTokenNotFoundException extends RuntimeException {
    public VerificationTokenNotFoundException() {
        super("Verification Token does not exist!");
    }
}