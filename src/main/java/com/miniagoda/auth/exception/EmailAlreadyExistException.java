package com.miniagoda.auth.exception;

public class EmailAlreadyExistException extends RuntimeException {
    public EmailAlreadyExistException() {
        super("Email already exists!!");
    }
}