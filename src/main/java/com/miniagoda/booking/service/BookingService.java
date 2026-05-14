package com.miniagoda.booking.service;

import org.springframework.stereotype.Service;

import com.miniagoda.booking.dto.BookingRequest;
import com.miniagoda.booking.dto.BookingResponse;

@Service
public class BookingService {
    public BookingResponse book(BookingRequest bookingRequest) {
        return new BookingResponse();
    }
}