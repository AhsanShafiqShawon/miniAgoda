package com.miniagoda.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.miniagoda.booking.entity.BookingStatus;

import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingResponse {
    private UUID bookingId;
    private UUID roomTypeId;
    private String roomTypeName;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int roomsBooked;
    private BigDecimal totalPrice;
    private BookingStatus status;
    private LocalDateTime expiredAt;
}