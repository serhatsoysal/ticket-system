package com.heditra.inventoryservice.repository;

import com.heditra.inventoryservice.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByEventName(String eventName);

    List<Inventory> findByAvailableSeatsGreaterThan(Integer seats);

    List<Inventory> findByEventDateBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT i FROM Inventory i WHERE i.availableSeats > 0 AND i.eventDate > :now")
    List<Inventory> findAvailableEvents(@Param("now") LocalDateTime now);

    boolean existsByEventName(String eventName);
}

