package com.miniagoda.notification.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class BookingCancelledEvent {
    UUID bookingId;
    String guestName;
    String guestEmail;
    String hotelName;
    LocalDate checkIn;
    LocalDate checkOut;
    BigDecimal totalAmount;
    String currency;
    String cancellationReason;
}