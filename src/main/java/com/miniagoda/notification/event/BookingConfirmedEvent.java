package com.miniagoda.notification.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {

    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @NotBlank(message = "Guest Name is required")
    private String guestName;

    @Email(message = "Guest Email must be a valid email address")
    private String guestEmail;
    
    @NotBlank(message = "Hotel Name is required")
    private String hotelName;

    @NotNull(message = "CheckIn is required")
    private LocalDate checkIn;

    @NotNull(message = "CheckOut is required")
    private LocalDate checkOut;

    @NotNull(message = "Total Amount is required")
    private BigDecimal totalAmount;

    @NotBlank(message = "Currency is required")
    private String currency;
}