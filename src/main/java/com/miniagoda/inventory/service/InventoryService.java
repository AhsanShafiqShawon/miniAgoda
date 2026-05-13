package com.miniagoda.inventory.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
public class InventoryService {
    public boolean isAvailable(LocalDate checkIn, LocalDate checkOut, Integer guests, Integer rooms) {
        return true;
    }
}