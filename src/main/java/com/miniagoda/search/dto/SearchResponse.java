package com.miniagoda.search.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {
    UUID hotelId;
    String hotelName;
    String hotelAddress;
    BigDecimal rating;
    BigDecimal price;
}