package com.heditra.inventoryservice.service.impl;

import com.heditra.inventoryservice.exception.BusinessException;
import com.heditra.inventoryservice.exception.InventoryNotFoundException;
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
            throw new BusinessException("Inventory already exists for event: " + inventory.getEventName(), "INVENTORY_ALREADY_EXISTS");
        }

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory created successfully with ID: {}", savedInventory.getId());
        return savedInventory;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "inventory", key = "#id")
    public Inventory getInventoryById(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "inventory", key = "'event:' + #eventName")
    public Inventory getInventoryByEventName(String eventName) {
        return inventoryRepository.findByEventName(eventName)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for event: " + eventName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "availableEvents")
    public List<Inventory> getAvailableEvents() {
        return inventoryRepository.findAvailableEvents(LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Inventory> getEventsByDateRange(LocalDateTime start, LocalDateTime end) {
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
                    Inventory inventory = getInventoryById(inventoryId);

                    if (inventory.getAvailableSeats() < quantity) {
                        return false;
                    }

                    inventory.setAvailableSeats(inventory.getAvailableSeats() - quantity);
                    inventoryRepository.save(inventory);

                    return true;
                } finally {
                    lock.unlock();
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
                    Inventory inventory = getInventoryById(inventoryId);

                    Integer newAvailableSeats = inventory.getAvailableSeats() + quantity;
                    if (newAvailableSeats > inventory.getTotalSeats()) {
                        return false;
                    }

                    inventory.setAvailableSeats(newAvailableSeats);
                    inventoryRepository.save(inventory);

                    return true;
                } finally {
                    lock.unlock();
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
        if (inventory == null) {
            throw new BusinessException("Inventory cannot be null", "INVALID_INVENTORY");
        }
        if (inventory.getAvailableSeats() == null || inventory.getTotalSeats() == null) {
            throw new BusinessException("Available seats and total seats cannot be null", "INVALID_INVENTORY");
        }
        if (inventory.getAvailableSeats() > inventory.getTotalSeats()) {
            throw new BusinessException("Available seats cannot exceed total seats", "INVALID_INVENTORY");
        }
        if (inventory.getEventDate() == null) {
            throw new BusinessException("Event date cannot be null", "INVALID_EVENT_DATE");
        }
        if (inventory.getEventDate().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Event date must be in the future", "INVALID_EVENT_DATE");
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

