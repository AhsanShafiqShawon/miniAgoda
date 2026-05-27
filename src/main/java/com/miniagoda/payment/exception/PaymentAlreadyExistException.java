package com.miniagoda.payment.exception;

public class PaymentAlreadyExistException extends RuntimeException {
    public PaymentAlreadyExistException() {
        super("Payment already exists for this booking");
    }
}