package com.miniagoda.hotel.controller;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.hotel.entity.Hotel;

public interface HotelController extends JpaRepository<Hotel, Long> {
    List<Hotel> findByCityIgnoreCase(String city);
    List<Hotel> findByNameContainingIgnoreCase(String keyword);
}