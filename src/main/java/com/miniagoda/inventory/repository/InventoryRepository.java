package com.miniagoda.inventory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.miniagoda.inventory.entity.Inventory;

import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    List<Inventory> findByRoomTypeIdAndDateBetween(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.roomType.id = :roomTypeId AND i.date BETWEEN :checkIn AND :checkOut ORDER BY i.date ASC")
    List<Inventory> findByRoomTypeIdAndDateBetweenWithLock(@Param("roomTypeId") UUID roomTypeId, @Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);
}