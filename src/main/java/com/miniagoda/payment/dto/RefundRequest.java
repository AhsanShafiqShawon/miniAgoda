package com.miniagoda.payment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    
    @NotNull(message = "Payment ID is required")
    private UUID paymentId;

    private String reason;
}