package com.miniagoda.search.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.miniagoda.search.dto.SearchRequest;
import com.miniagoda.search.dto.SearchResponse;

@Service
public class SearchService {
    public List<SearchResponse> search(SearchRequest searchRequest) {
        List<SearchResponse> result = new ArrayList<>();
        
        result.add(SearchResponse.builder()
        .hotelName("Picnic Hotel")
        .hotelAddress("Rangnam Alley")
        .rating(4.9)
        .price(1200.0)
        .build());

        result.add(SearchResponse.builder()
        .hotelName("Hotel Palladium")
        .hotelAddress("Pratunam Area")
        .rating(4.8)
        .price(1500.0)
        .build());

        return result;
    }
}