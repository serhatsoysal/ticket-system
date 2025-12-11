package com.heditra.ticketservice.repository;

import com.heditra.ticketservice.model.Ticket;
import com.heditra.ticketservice.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByUserId(Long userId);

    List<Ticket> findByStatus(TicketStatus status);

    List<Ticket> findByEventName(String eventName);

    List<Ticket> findByUserIdAndStatus(Long userId, TicketStatus status);

    List<Ticket> findByEventDateBetween(LocalDateTime start, LocalDateTime end);
}

