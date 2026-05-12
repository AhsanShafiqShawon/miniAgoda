package com.miniagoda.hotel.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.hotel.entity.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {
    Optional<Hotel> findByCode(String code);
}