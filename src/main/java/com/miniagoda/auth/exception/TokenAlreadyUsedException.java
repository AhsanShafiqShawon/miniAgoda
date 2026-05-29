package com.miniagoda.auth.exception;

public class TokenAlreadyUsedException extends RuntimeException {
    public TokenAlreadyUsedException() {
        super("This token already used!!");
    }
}