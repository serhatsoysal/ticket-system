package com.heditra.ticketservice.cqrs.commands.handlers;

import com.heditra.events.core.EventPublisher;
import com.heditra.events.ticket.TicketCancelledEvent;
import com.heditra.ticketservice.cqrs.commands.CancelTicketCommand;
import com.heditra.ticketservice.cqrs.common.CommandResult;
import com.heditra.ticketservice.model.Ticket;
import com.heditra.ticketservice.model.TicketStatus;
import com.heditra.ticketservice.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelTicketCommandHandler {
    
    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    private final WebClient webClient;
    
    @Transactional
    public CommandResult<Void> handle(CancelTicketCommand command) {
        Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElse(null);
        
        if (ticket == null) {
            return CommandResult.failure("Ticket not found", "TICKET_NOT_FOUND");
        }
        
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            return CommandResult.failure("Ticket is already cancelled", "ALREADY_CANCELLED");
        }
        
        releaseSeatsInInventory(ticket.getEventName(), ticket.getQuantity());
        
        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setUpdatedAt(java.time.LocalDateTime.now());
        ticketRepository.save(ticket);
        
        publishTicketCancelledEvent(ticket, command.getCancellationReason());
        
        return CommandResult.success(null, "Ticket cancelled successfully");
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
    
    private void publishTicketCancelledEvent(Ticket ticket, String reason) {
        TicketCancelledEvent event = TicketCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateId(ticket.getId().toString())
                .version(2)
                .ticketId(ticket.getId())
                .userId(ticket.getUserId())
                .eventName(ticket.getEventName())
                .quantity(ticket.getQuantity())
                .cancellationReason(reason != null ? reason : "User requested cancellation")
                .build();
        
        eventPublisher.publish("ticket-cancelled", event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish TicketCancelledEvent for ticket ID: {}", ticket.getId(), ex);
                    }
                });
    }
}
