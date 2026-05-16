package com.miniagoda.payment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateRequest {
    
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;
}