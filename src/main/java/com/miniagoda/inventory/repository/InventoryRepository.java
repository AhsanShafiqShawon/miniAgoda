package com.miniagoda.inventory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.inventory.entity.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    List<Inventory> findByRoomTypeIdAndDateBetween(UUID roomTypeId,
        LocalDate checkIn,
        LocalDate checOut);
}