package com.miniagoda.search.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miniagoda.search.dto.SearchRequest;
import com.miniagoda.search.dto.SearchResponse;
import com.miniagoda.search.service.SearchService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/hotels")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResponse>> search(@Valid @ModelAttribute SearchRequest searchRequest) {
        List<SearchResponse> hotels = searchService.search(searchRequest);
        return ResponseEntity.ok(hotels);
    }
}