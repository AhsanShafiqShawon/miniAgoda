package com.miniagoda.booking.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.booking.entity.Booking;

public interface BookingRepository extends JpaRepository<Booking, UUID> {}