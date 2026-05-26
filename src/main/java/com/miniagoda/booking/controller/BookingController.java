package com.miniagoda.booking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.dto.BookingResponse;
import com.miniagoda.booking.service.BookingService;

import jakarta.validation.Valid;

@RestController
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> creatBooking(@Valid @RequestBody BookingRequest bookingRequest) {
        BookingResponse response = bookingService.createBooking(bookingRequest);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<BookingResponse>> getBookings() {
        List<BookingResponse> responses = bookingService.getBookings();
        return ResponseEntity.ok(responses);
    }
}