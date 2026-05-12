package com.miniagoda.inventory.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miniagoda.inventory.entity.Inventory;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {}