package com.miniagoda.inventory.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class InventoryService {
    public boolean isAvailable(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, Integer guests, Integer rooms) {
        return true;
    }
}