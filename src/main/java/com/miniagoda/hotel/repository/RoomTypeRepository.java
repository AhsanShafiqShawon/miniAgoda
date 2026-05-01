package com.miniagoda.hotel.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.hotel.entity.RoomType;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {}