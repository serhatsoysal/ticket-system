package com.heditra.inventoryservice.service;

import com.heditra.inventoryservice.model.Inventory;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryService {

    Inventory createInventory(Inventory inventory);

    Inventory getInventoryById(Long id);

    Inventory getInventoryByEventName(String eventName);

    List<Inventory> getAllInventory();

    List<Inventory> getAvailableEvents();

    List<Inventory> getEventsByDateRange(LocalDateTime start, LocalDateTime end);

    Inventory updateInventory(Long id, Inventory inventory);

    boolean reserveSeats(Long inventoryId, Integer quantity);

    boolean releaseSeats(Long inventoryId, Integer quantity);

    void deleteInventory(Long id);
}

