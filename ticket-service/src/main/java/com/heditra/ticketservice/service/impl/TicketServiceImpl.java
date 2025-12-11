package com.heditra.ticketservice.service.impl;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final String TICKET_CREATED_TOPIC = "ticket-created";
    private static final String TICKET_UPDATED_TOPIC = "ticket-updated";
    private static final String TICKET_CANCELLED_TOPIC = "ticket-cancelled";

    private final TicketRepository ticketRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebClient webClient;

    @Override
    @Transactional
    public Ticket createTicket(Ticket ticket) {
        log.info("Creating new ticket for user: {} and event: {}", 
                ticket.getUserId(), ticket.getEventName());

        ticket.setStatus(TicketStatus.PENDING);

        boolean seatsReserved = reserveSeatsInInventory(ticket.getEventName(), ticket.getQuantity());
        if (!seatsReserved) {
            throw new RuntimeException("Unable to reserve seats. Insufficient availability.");
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        publishTicketEvent(TICKET_CREATED_TOPIC, savedTicket);

        log.info("Ticket created successfully with ID: {}", savedTicket.getId());
        return savedTicket;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "tickets", key = "#id")
    public Ticket getTicketById(Long id) {
        log.debug("Fetching ticket by ID: {}", id);
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found with ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        log.debug("Fetching all tickets");
        return ticketRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByUserId(Long userId) {
        log.debug("Fetching tickets for user ID: {}", userId);
        return ticketRepository.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByStatus(TicketStatus status) {
        log.debug("Fetching tickets by status: {}", status);
        return ticketRepository.findByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByEventName(String eventName) {
        log.debug("Fetching tickets for event: {}", eventName);
        return ticketRepository.findByEventName(eventName);
    }

    @Override
    @Transactional
    public Ticket updateTicketStatus(Long id, TicketStatus status) {
        log.info("Updating ticket status to {} for ID: {}", status, id);

        Ticket ticket = getTicketById(id);
        ticket.setStatus(status);

        Ticket updatedTicket = ticketRepository.save(ticket);
        publishTicketEvent(TICKET_UPDATED_TOPIC, updatedTicket);

        log.info("Ticket status updated successfully for ID: {}", id);
        return updatedTicket;
    }

    @Override
    @Transactional
    public Ticket cancelTicket(Long id) {
        log.info("Cancelling ticket with ID: {}", id);

        Ticket ticket = getTicketById(id);

        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new RuntimeException("Ticket is already cancelled");
        }

        releaseSeatsInInventory(ticket.getEventName(), ticket.getQuantity());

        ticket.setStatus(TicketStatus.CANCELLED);
        Ticket cancelledTicket = ticketRepository.save(ticket);
        publishTicketEvent(TICKET_CANCELLED_TOPIC, cancelledTicket);

        log.info("Ticket cancelled successfully with ID: {}", id);
        return cancelledTicket;
    }

    @Override
    @Transactional
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
            throw new RuntimeException("Failed to reserve seats", e);
        }
    }

    private boolean reserveSeatsFallback(String eventName, Integer quantity, Exception e) {
        log.error("Fallback: Unable to reserve seats for event: {}", eventName, e);
        return false;
    }

    private void releaseSeatsInInventory(String eventName, Integer quantity) {
        try {
            webClient.post()
                    .uri("http://inventory-service/inventory/event/{eventName}/release?quantity={quantity}", 
                         eventName, quantity)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
        } catch (Exception e) {
            log.error("Error releasing seats in inventory service", e);
        }
    }

    private void publishTicketEvent(String topic, Ticket ticket) {
        kafkaTemplate.send(topic, ticket.getId().toString(), ticket)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Ticket event published to topic: {}", topic);
                    } else {
                        log.error("Failed to publish ticket event to topic: {}", topic, ex);
                    }
                });
    }
}

