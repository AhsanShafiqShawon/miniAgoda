package com.miniagoda.hotel.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.hotel.entity.Hotel;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {
    List<Hotel> findByCityIgnoreCase(String destination);
    Optional<Hotel> findByCode(String code);
}