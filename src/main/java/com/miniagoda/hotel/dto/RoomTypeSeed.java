package com.miniagoda.hotel.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomTypeSeed {
    private String name;
    private Integer capacity;
    private BigDecimal price;
    private Integer totalUnits;
    private String hotelCode;
}