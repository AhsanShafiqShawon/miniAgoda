package com.miniagoda.payment.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefundResponse {
    private UUID paymentId;
    private BigDecimal amount;
    private String currency;
}