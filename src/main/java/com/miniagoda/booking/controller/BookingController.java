package com.miniagoda.booking.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.dto.BookingResponse;
import com.miniagoda.booking.service.BookingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Validated
public class BookingController {

    private final BookingService bookingService;
    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/booking")
    public ResponseEntity<BookingResponse> creatBooking(@Valid @RequestBody BookingRequest bookingRequest) {
        long start = System.currentTimeMillis();
        
        BookingResponse response = bookingService.createBooking(bookingRequest);
        log.info("[ASYNC] Controller returned in {} ms", System.currentTimeMillis() - start);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> getBookings() {
        List<BookingResponse> responses = bookingService.getBookings();
        return ResponseEntity.ok(responses);
    }
}