package com.miniagoda.inventory.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.miniagoda.inventory.entity.Inventory;
import com.miniagoda.inventory.repository.InventoryRepository;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public boolean isAvailable(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, Integer guests, Integer requestedRooms) {
        List<Inventory> inventories = inventoryRepository.findByRoomTypeIdAndDateBetween(roomTypeId, checkIn, checkOut.minusDays(1));
        
        Long expectedDays = ChronoUnit.DAYS.between(checkIn, checkOut);
        if(expectedDays != inventories.size()) return false;

        int availableDays = Integer.MAX_VALUE;
        for(Inventory inventory : inventories) {
            availableDays = Math.min(availableDays, inventory.getAvailableUnits());
        }
        
        return availableDays >= requestedRooms;
    }
}