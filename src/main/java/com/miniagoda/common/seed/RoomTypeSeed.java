package com.miniagoda.common.seed;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RoomTypeSeed {
    private String name;
    private Integer capacity;
    private Double price;
    private Integer totalUnits;
    private String hotelCode;
}