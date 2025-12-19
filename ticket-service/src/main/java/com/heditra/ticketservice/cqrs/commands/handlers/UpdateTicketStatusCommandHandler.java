package com.heditra.ticketservice.cqrs.commands.handlers;

import com.heditra.events.core.EventPublisher;
import com.heditra.events.ticket.TicketConfirmedEvent;
import com.heditra.ticketservice.cqrs.commands.UpdateTicketStatusCommand;
import com.heditra.ticketservice.cqrs.common.CommandResult;
import com.heditra.ticketservice.model.Ticket;
import com.heditra.ticketservice.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateTicketStatusCommandHandler {
    
    private final TicketRepository ticketRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public CommandResult<Void> handle(UpdateTicketStatusCommand command) {
        Ticket ticket = ticketRepository.findById(command.getTicketId())
                .orElse(null);
        
        if (ticket == null) {
            return CommandResult.failure("Ticket not found", "TICKET_NOT_FOUND");
        }
        
        if (ticket.getStatus() == com.heditra.ticketservice.model.TicketStatus.CANCELLED && command.getStatus() != com.heditra.ticketservice.model.TicketStatus.CANCELLED) {
            return CommandResult.failure("Cannot change status of a cancelled ticket", "TICKET_ALREADY_CANCELLED");
        }
        
        ticket.setStatus(command.getStatus());
        ticket.setUpdatedAt(java.time.LocalDateTime.now());
        ticketRepository.save(ticket);
        
        if (command.getStatus() == com.heditra.ticketservice.model.TicketStatus.CONFIRMED) {
            publishTicketConfirmedEvent(ticket);
        }
        
        return CommandResult.success(null, "Ticket status updated successfully");
    }
    
    private void publishTicketConfirmedEvent(Ticket ticket) {
        TicketConfirmedEvent event = TicketConfirmedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateId(ticket.getId().toString())
                .version(ticket.getVersion() != null ? (int) (ticket.getVersion() + 1) : 2)
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
}
