package com.miniagoda.hotel.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomTypeResponse {
    private UUID roomTypeId;
    private String roomTypeName;
    private int capacity;
    private BigDecimal price;
    private int availableRooms;
}