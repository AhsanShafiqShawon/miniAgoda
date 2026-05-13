package com.miniagoda.search.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Value;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {
    String hotelName;
    String hotelAddress;
    BigDecimal rating;
    BigDecimal price;
}