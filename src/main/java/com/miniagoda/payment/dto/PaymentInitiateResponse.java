package com.miniagoda.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentInitiateResponse {
    private UUID bookingId;
    private String clientSecret;
    private BigDecimal amount;
    private String currency;
}