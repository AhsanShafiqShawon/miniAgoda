package com.miniagoda.hotel.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelDetailRequest {
    
    @NotNull(message = "Hotel ID is required")
    private UUID hotelId;

    @NotNull(message = "Check-in date is required")
    @FutureOrPresent(message = "Check-in date must not be in the past")
    private LocalDate checkIn;

    @NotNull(message = "Check-out date is required")
    @Future(message = "Check-out date must be in the future")
    private LocalDate checkOut;

    @NotNull(message = "Number of guests is required")
    @Min(value = 1, message = "At least 1 adult is required")
    @Max(value = 30, message = "Maximum 30 adults allowed")
    private Integer guests;

    @NotNull(message = "Number of rooms is required")
    @Min(value = 1, message = "At least 1 room is required")
    @Max(value = 30, message = "Maximum 30 rooms allowed")
    private Integer rooms;

    @AssertTrue(message = "Check-out must be after check-in")
    private boolean isCheckOutAfterCheckIn() {
        if(checkIn == null || checkOut == null) return true;
        return checkOut.isAfter(checkIn);
    }
}