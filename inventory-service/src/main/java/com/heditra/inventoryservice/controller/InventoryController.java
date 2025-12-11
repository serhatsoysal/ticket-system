package com.heditra.inventoryservice.controller;

import com.heditra.inventoryservice.model.Inventory;
import com.heditra.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<Inventory> createInventory(@Valid @RequestBody Inventory inventory) {
        Inventory createdInventory = inventoryService.createInventory(inventory);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdInventory);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Inventory> getInventoryById(@PathVariable Long id) {
        Inventory inventory = inventoryService.getInventoryById(id);
        return ResponseEntity.ok(inventory);
    }

    @GetMapping("/event/{eventName}")
    public ResponseEntity<Inventory> getInventoryByEventName(@PathVariable String eventName) {
        Inventory inventory = inventoryService.getInventoryByEventName(eventName);
        return ResponseEntity.ok(inventory);
    }

    @GetMapping
    public ResponseEntity<List<Inventory>> getAllInventory() {
        List<Inventory> inventoryList = inventoryService.getAllInventory();
        return ResponseEntity.ok(inventoryList);
    }

    @GetMapping("/available")
    public ResponseEntity<List<Inventory>> getAvailableEvents() {
        List<Inventory> availableEvents = inventoryService.getAvailableEvents();
        return ResponseEntity.ok(availableEvents);
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<Inventory>> getEventsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<Inventory> events = inventoryService.getEventsByDateRange(start, end);
        return ResponseEntity.ok(events);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Inventory> updateInventory(@PathVariable Long id, 
                                                      @Valid @RequestBody Inventory inventory) {
        Inventory updatedInventory = inventoryService.updateInventory(id, inventory);
        return ResponseEntity.ok(updatedInventory);
    }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<Boolean> reserveSeats(@PathVariable Long id, 
                                                 @RequestParam Integer quantity) {
        boolean reserved = inventoryService.reserveSeats(id, quantity);
        return ResponseEntity.ok(reserved);
    }

    @PostMapping("/event/{eventName}/reserve")
    public ResponseEntity<Boolean> reserveSeatsByEventName(@PathVariable String eventName, 
                                                            @RequestParam Integer quantity) {
        Inventory inventory = inventoryService.getInventoryByEventName(eventName);
        boolean reserved = inventoryService.reserveSeats(inventory.getId(), quantity);
        return ResponseEntity.ok(reserved);
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<Boolean> releaseSeats(@PathVariable Long id, 
                                                 @RequestParam Integer quantity) {
        boolean released = inventoryService.releaseSeats(id, quantity);
        return ResponseEntity.ok(released);
    }

    @PostMapping("/event/{eventName}/release")
    public ResponseEntity<Boolean> releaseSeatsByEventName(@PathVariable String eventName, 
                                                            @RequestParam Integer quantity) {
        Inventory inventory = inventoryService.getInventoryByEventName(eventName);
        boolean released = inventoryService.releaseSeats(inventory.getId(), quantity);
        return ResponseEntity.ok(released);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInventory(@PathVariable Long id) {
        inventoryService.deleteInventory(id);
        return ResponseEntity.noContent().build();
    }
}

