package com.heditra.ticketservice.service.impl;

import com.heditra.events.core.EventPublisher;
import com.heditra.events.ticket.TicketCancelledEvent;
import com.heditra.events.ticket.TicketCreatedEvent;
import com.heditra.events.ticket.TicketConfirmedEvent;
import com.heditra.ticketservice.exception.BusinessException;
import com.heditra.ticketservice.exception.TechnicalException;
import com.heditra.ticketservice.exception.TicketNotFoundException;
import com.heditra.ticketservice.exception.ValidationException;
import com.heditra.ticketservice.model.Ticket;
import com.heditra.ticketservice.model.TicketStatus;
import com.heditra.ticketservice.repository.TicketRepository;
import com.heditra.ticketservice.service.TicketService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    private final WebClient webClient;

    @Override
    @Transactional
    public Ticket createTicket(Ticket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        if (ticket.getUserId() == null || ticket.getEventName() == null || ticket.getQuantity() == null || ticket.getPricePerTicket() == null) {
            throw new ValidationException("Missing required ticket fields", "VALIDATION_ERROR");
        }
        if (ticket.getQuantity() <= 0) {
            throw new ValidationException("Quantity must be greater than zero", "INVALID_QUANTITY");
        }
        if (ticket.getPricePerTicket().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Price per ticket must be greater than zero", "INVALID_PRICE");
        }
        
        log.info("Creating new ticket for user: {} and event: {}", 
                ticket.getUserId(), ticket.getEventName());

        ticket.setStatus(TicketStatus.PENDING);
        
        if (ticket.getTotalAmount() == null) {
            ticket.setTotalAmount(ticket.getPricePerTicket().multiply(java.math.BigDecimal.valueOf(ticket.getQuantity())));
        }

        boolean seatsReserved = reserveSeatsInInventory(ticket.getEventName(), ticket.getQuantity());
        if (!seatsReserved) {
            throw new BusinessException("Unable to reserve seats. Insufficient availability.", "INVENTORY_UNAVAILABLE");
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        
        publishTicketCreatedEvent(savedTicket);

        log.info("Ticket created successfully with ID: {}", savedTicket.getId());
        return savedTicket;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "tickets", key = "#id")
    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByUserId(Long userId) {
        return ticketRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByStatus(TicketStatus status) {
        return ticketRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByEventName(String eventName) {
        return ticketRepository.findByEventName(eventName);
    }

    @Override
    @Transactional
    @CacheEvict(value = "tickets", key = "#id")
    public Ticket updateTicketStatus(Long id, TicketStatus status) {
        log.info("Updating ticket status to {} for ID: {}", status, id);

        Ticket ticket = getTicketById(id);
        
        if (ticket.getStatus() == TicketStatus.CANCELLED && status != TicketStatus.CANCELLED) {
            throw new BusinessException("Cannot change status of a cancelled ticket", "TICKET_ALREADY_CANCELLED");
        }
        
        ticket.setStatus(status);

        Ticket updatedTicket = ticketRepository.save(ticket);
        
        if (status == TicketStatus.CONFIRMED) {
            publishTicketConfirmedEvent(updatedTicket);
        }

        log.info("Ticket status updated successfully for ID: {}", id);
        return updatedTicket;
    }

    @Override
    @Transactional
    public Ticket cancelTicket(Long id) {
        log.info("Cancelling ticket with ID: {}", id);

        Ticket ticket = getTicketById(id);

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new BusinessException("Ticket is already cancelled", "TICKET_ALREADY_CANCELLED");
        }

        releaseSeatsInInventory(ticket.getEventName(), ticket.getQuantity());

        ticket.setStatus(TicketStatus.CANCELLED);
        Ticket cancelledTicket = ticketRepository.save(ticket);
        
        publishTicketCancelledEvent(cancelledTicket);

        log.info("Ticket cancelled successfully with ID: {}", id);
        return cancelledTicket;
    }

    @Override
    @Transactional
    @CacheEvict(value = "tickets", key = "#id")
    public void deleteTicket(Long id) {
        log.info("Deleting ticket with ID: {}", id);

        Ticket ticket = getTicketById(id);
        ticketRepository.delete(ticket);

        log.info("Ticket deleted successfully with ID: {}", id);
    }

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "reserveSeatsFallback")
    @Retry(name = "inventory-service")
    private boolean reserveSeatsInInventory(String eventName, Integer quantity) {
        try {
            Boolean result = webClient.post()
                    .uri("http://inventory-service/inventory/event/{eventName}/reserve?quantity={quantity}", 
                         eventName, quantity)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error reserving seats in inventory service", e);
            throw new TechnicalException("Failed to reserve seats", e);
        }
    }

    private boolean reserveSeatsFallback(String eventName, Integer quantity, Exception e) {
        log.error("Fallback: Unable to reserve seats for event: {}", eventName, e);
        return false;
    }

    private void releaseSeatsInInventory(String eventName, Integer quantity) {
        if (eventName == null || quantity == null || quantity <= 0) {
            log.warn("Invalid parameters for releasing seats: eventName={}, quantity={}", eventName, quantity);
            return;
        }
        try {
            Boolean result = webClient.post()
                    .uri("http://inventory-service/inventory/event/{eventName}/release?quantity={quantity}", 
                         eventName, quantity)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            if (Boolean.FALSE.equals(result)) {
                log.warn("Failed to release {} seats for event: {}", quantity, eventName);
            }
        } catch (Exception e) {
            log.error("Error releasing seats in inventory service for event: {}, quantity: {}", eventName, quantity, e);
        }
    }

    private void publishTicketCreatedEvent(Ticket ticket) {
        TicketCreatedEvent event = TicketCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateId(ticket.getId().toString())
                .version(1)
                .ticketId(ticket.getId())
                .userId(ticket.getUserId())
                .eventName(ticket.getEventName())
                .quantity(ticket.getQuantity())
                .pricePerTicket(ticket.getPricePerTicket())
                .totalAmount(ticket.getTotalAmount())
                .build();
        
        eventPublisher.publish("ticket-created", event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TicketCreatedEvent for ticket ID: {}", ticket.getId(), ex);
                    }
                });
    }

    private void publishTicketConfirmedEvent(Ticket ticket) {
        TicketConfirmedEvent event = TicketConfirmedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateId(ticket.getId().toString())
                .version(ticket.getVersion() != null ? (int) (ticket.getVersion() + 1) : 1)
                .ticketId(ticket.getId())
                .userId(ticket.getUserId())
                .eventName(ticket.getEventName())
                .build();
        
        eventPublisher.publish("ticket-confirmed", event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TicketConfirmedEvent for ticket ID: {}", ticket.getId(), ex);
                    }
                });
    }

    private void publishTicketCancelledEvent(Ticket ticket) {
        TicketCancelledEvent event = TicketCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateId(ticket.getId().toString())
                .version(ticket.getVersion() != null ? (int) (ticket.getVersion() + 1) : 1)
                .ticketId(ticket.getId())
                .userId(ticket.getUserId())
                .eventName(ticket.getEventName())
                .quantity(ticket.getQuantity())
                .cancellationReason("User requested cancellation")
                .build();
        
        eventPublisher.publish("ticket-cancelled", event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TicketCancelledEvent for ticket ID: {}", ticket.getId(), ex);
                    }
                });
    }
}

