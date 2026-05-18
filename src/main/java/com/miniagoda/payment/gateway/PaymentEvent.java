package com.miniagoda.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    
    @NotNull(message = "Status is required")
    private PaymentEventStatus status;

    @NotNull(message = "Gateway Payment ID is required")
    private String gatewayPaymentId;

    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;
    
    private String failureMessage;
}