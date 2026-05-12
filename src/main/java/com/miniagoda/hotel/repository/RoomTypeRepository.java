package com.miniagoda.hotel.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.hotel.entity.RoomType;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {}