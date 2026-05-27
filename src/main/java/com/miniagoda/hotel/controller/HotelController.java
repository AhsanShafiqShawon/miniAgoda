package com.miniagoda.hotel.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miniagoda.hotel.dto.HotelDetailRequest;
import com.miniagoda.hotel.dto.HotelDetailResponse;
import com.miniagoda.hotel.service.HotelService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }
    
    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<HotelDetailResponse> getHotelDetail(@Valid @ModelAttribute HotelDetailRequest hotelDetailRequest) {
        HotelDetailResponse detail = hotelService.getHotelDetail(hotelDetailRequest);
        return ResponseEntity.ok(detail);
    }
}