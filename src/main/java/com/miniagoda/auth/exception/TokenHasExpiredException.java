package com.miniagoda.auth.exception;

public class TokenHasExpiredException extends RuntimeException {
    public TokenHasExpiredException() {
        super("Token has Expired!!");
    }
}