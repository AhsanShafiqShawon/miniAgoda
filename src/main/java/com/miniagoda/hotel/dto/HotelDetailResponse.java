package com.miniagoda.hotel.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HotelDetailResponse {
    private UUID hotelId;
    private String hotelName;
    private String hotelAddress;
    private BigDecimal rating;
    private List<RoomTypeResponse> roomTypes;
}