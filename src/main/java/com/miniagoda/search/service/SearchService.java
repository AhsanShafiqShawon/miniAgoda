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
        
        return result;
    }
}