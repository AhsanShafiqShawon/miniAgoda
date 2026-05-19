package com.miniagoda.payment.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentEvent {

    public enum Type {
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED,
        REFUND_SUCCEEDED,
        UNKNOWN
    }

    private Type type;
    private String paymentId;
    private String bookingId;
    private Long amount;
    private String currency;
    private String failureMessage;
}