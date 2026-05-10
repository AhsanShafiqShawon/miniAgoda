package com.miniagoda.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {
    String hotelName;
    String hotelAddress;
    Double rating;
    Double price;
}