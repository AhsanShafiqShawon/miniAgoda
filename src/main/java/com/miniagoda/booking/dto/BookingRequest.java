package com.miniagoda.booking.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {
    
    @NotNull(message = "RoomType ID is required")
    private UUID roomTypeId;
    
    @NotNull(message = "Check-in date is required")
    @FutureOrPresent(message = "Check-in date must not be in the past")
    private LocalDate checkIn;

    @NotNull(message = "Check-out date is required")
    @Future(message = "Check-out date must be in the future")
    private LocalDate checkOut;

    @NotNull(message = "Number of rooms is required")
    @Min(value = 1, message = "At least 1 room is required")
    @Max(value = 30, message = "Maximum 30 rooms allowed")
    private int roomsRequested;

    @AssertTrue(message = "Check-out must be after check-in")
    private boolean isCheckOutAfterCheckIn() {
        if(checkIn == null || checkOut == null) return true;
        return checkOut.isAfter(checkIn);
    }
}