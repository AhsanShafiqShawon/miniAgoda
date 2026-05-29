package com.miniagoda.notification.event;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class PaymentSuccessEvent {
    String payingUserEmail;
    String payingUserName;
    BigDecimal amount;
    String currency;
    UUID bookingId;
}