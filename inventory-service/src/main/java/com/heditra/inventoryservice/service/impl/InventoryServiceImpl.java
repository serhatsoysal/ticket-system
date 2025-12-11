package com.heditra.inventoryservice.service.impl;

import com.heditra.inventoryservice.model.Inventory;
import com.heditra.inventoryservice.repository.InventoryRepository;
import com.heditra.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final String INVENTORY_LOCK_KEY = "inventory:lock:";
    private static final long LOCK_WAIT_TIME = 10;
    private static final long LOCK_LEASE_TIME = 5;

    private final InventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;

    @Override
    @Transactional
    public Inventory createInventory(Inventory inventory) {
        log.info("Creating new inventory for event: {}", inventory.getEventName());

        validateInventory(inventory);

        if (inventoryRepository.existsByEventName(inventory.getEventName())) {
            throw new RuntimeException("Inventory already exists for event: " + inventory.getEventName());
        }

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory created successfully with ID: {}", savedInventory.getId());
        return savedInventory;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "inventory", key = "#id")
    public Inventory getInventoryById(Long id) {
        log.debug("Fetching inventory by ID: {}", id);
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "inventory", key = "'event:' + #eventName")
    public Inventory getInventoryByEventName(String eventName) {
        log.debug("Fetching inventory by event name: {}", eventName);
        return inventoryRepository.findByEventName(eventName)
                .orElseThrow(() -> new RuntimeException("Inventory not found for event: " + eventName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Inventory> getAllInventory() {
        log.debug("Fetching all inventory");
        return inventoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "availableEvents")
    public List<Inventory> getAvailableEvents() {
        log.debug("Fetching available events");
        return inventoryRepository.findAvailableEvents(LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Inventory> getEventsByDateRange(LocalDateTime start, LocalDateTime end) {
        log.debug("Fetching events between {} and {}", start, end);
        return inventoryRepository.findByEventDateBetween(start, end);
    }

    @Override
    @Transactional
    @CachePut(value = "inventory", key = "#id")
    @CacheEvict(value = "availableEvents", allEntries = true)
    public Inventory updateInventory(Long id, Inventory inventory) {
        log.info("Updating inventory with ID: {}", id);

        Inventory existingInventory = getInventoryById(id);
        updateInventoryFields(existingInventory, inventory);

        Inventory updatedInventory = inventoryRepository.save(existingInventory);
        log.info("Inventory updated successfully with ID: {}", id);
        return updatedInventory;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = {"inventory", "availableEvents"}, allEntries = true)
    public boolean reserveSeats(Long inventoryId, Integer quantity) {
        String lockKey = INVENTORY_LOCK_KEY + inventoryId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    log.info("Acquired lock for reserving {} seats for inventory ID: {}", quantity, inventoryId);

                    Inventory inventory = getInventoryById(inventoryId);

                    if (inventory.getAvailableSeats() < quantity) {
                        log.warn("Insufficient seats. Available: {}, Requested: {}", 
                                inventory.getAvailableSeats(), quantity);
                        return false;
                    }

                    inventory.setAvailableSeats(inventory.getAvailableSeats() - quantity);
                    inventoryRepository.save(inventory);

                    log.info("Successfully reserved {} seats for inventory ID: {}", quantity, inventoryId);
                    return true;
                } finally {
                    lock.unlock();
                    log.debug("Released lock for inventory ID: {}", inventoryId);
                }
            } else {
                log.warn("Could not acquire lock for inventory ID: {}", inventoryId);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting for lock", e);
            return false;
        }
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CacheEvict(value = {"inventory", "availableEvents"}, allEntries = true)
    public boolean releaseSeats(Long inventoryId, Integer quantity) {
        String lockKey = INVENTORY_LOCK_KEY + inventoryId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    log.info("Acquired lock for releasing {} seats for inventory ID: {}", quantity, inventoryId);

                    Inventory inventory = getInventoryById(inventoryId);

                    Integer newAvailableSeats = inventory.getAvailableSeats() + quantity;
                    if (newAvailableSeats > inventory.getTotalSeats()) {
                        log.warn("Cannot release more seats than total capacity");
                        return false;
                    }

                    inventory.setAvailableSeats(newAvailableSeats);
                    inventoryRepository.save(inventory);

                    log.info("Successfully released {} seats for inventory ID: {}", quantity, inventoryId);
                    return true;
                } finally {
                    lock.unlock();
                    log.debug("Released lock for inventory ID: {}", inventoryId);
                }
            } else {
                log.warn("Could not acquire lock for inventory ID: {}", inventoryId);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while waiting for lock", e);
            return false;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"inventory", "availableEvents"}, allEntries = true)
    public void deleteInventory(Long id) {
        log.info("Deleting inventory with ID: {}", id);

        Inventory inventory = getInventoryById(id);
        inventoryRepository.delete(inventory);

        log.info("Inventory deleted successfully with ID: {}", id);
    }

    private void validateInventory(Inventory inventory) {
        if (inventory.getAvailableSeats() > inventory.getTotalSeats()) {
            throw new RuntimeException("Available seats cannot exceed total seats");
        }
        if (inventory.getEventDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Event date must be in the future");
        }
    }

    private void updateInventoryFields(Inventory existing, Inventory updated) {
        existing.setEventName(updated.getEventName());
        existing.setEventDate(updated.getEventDate());
        existing.setTotalSeats(updated.getTotalSeats());
        existing.setAvailableSeats(updated.getAvailableSeats());
        existing.setPrice(updated.getPrice());
        existing.setLocation(updated.getLocation());
    }
}

