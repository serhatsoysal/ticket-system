package com.heditra.ticketservice.cqrs.commands.handlers;

import com.heditra.events.core.EventPublisher;
import com.heditra.events.ticket.TicketCreatedEvent;
import com.heditra.ticketservice.cqrs.commands.CreateTicketCommand;
import com.heditra.ticketservice.cqrs.common.CommandResult;
import com.heditra.ticketservice.model.Ticket;
import com.heditra.ticketservice.model.TicketStatus;
import com.heditra.ticketservice.repository.TicketRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CreateTicketCommandHandler {
    
    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    private final WebClient webClient;
    
    @Transactional
    @CircuitBreaker(name = "inventory-service", fallbackMethod = "handleInventoryFailure")
    public CommandResult<Long> handle(CreateTicketCommand command) {
        if (command.getUserId() == null || command.getEventName() == null || command.getQuantity() == null || command.getPricePerTicket() == null) {
            return CommandResult.failure("Invalid command parameters", "VALIDATION_ERROR");
        }
        
        if (command.getQuantity() <= 0) {
            return CommandResult.failure("Quantity must be greater than zero", "INVALID_QUANTITY");
        }
        
        if (command.getPricePerTicket().compareTo(BigDecimal.ZERO) <= 0) {
            return CommandResult.failure("Price per ticket must be greater than zero", "INVALID_PRICE");
        }
        
        boolean seatsReserved = reserveSeatsInInventory(command.getEventName(), command.getQuantity());
        if (!seatsReserved) {
            return CommandResult.failure("Unable to reserve seats. Insufficient availability.", "INVENTORY_UNAVAILABLE");
        }
        
        Ticket ticket = new Ticket();
        ticket.setUserId(command.getUserId());
        ticket.setEventName(command.getEventName());
        ticket.setQuantity(command.getQuantity());
        ticket.setPricePerTicket(command.getPricePerTicket());
        ticket.setTotalAmount(command.getPricePerTicket().multiply(BigDecimal.valueOf(command.getQuantity())));
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setCreatedAt(LocalDateTime.now());
        
        Ticket savedTicket = ticketRepository.save(ticket);
        
        publishTicketCreatedEvent(savedTicket);
        
        log.info("Ticket created successfully with ID: {}", savedTicket.getId());
        return CommandResult.success(savedTicket.getId(), "Ticket created successfully");
    }
    
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
            return false;
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
    
    @SuppressWarnings("unused")
    private CommandResult<Long> handleInventoryFailure(CreateTicketCommand command, Exception e) {
        log.error("Fallback: Unable to create ticket due to inventory service failure", e);
        return CommandResult.failure("Service temporarily unavailable", "SERVICE_UNAVAILABLE");
    }
}
