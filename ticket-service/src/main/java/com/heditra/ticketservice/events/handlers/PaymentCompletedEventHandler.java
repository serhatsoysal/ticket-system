package com.heditra.ticketservice.events.handlers;

import com.heditra.events.core.EventHandler;
import com.heditra.events.payment.PaymentCompletedEvent;
import com.heditra.ticketservice.model.TicketStatus;
import com.heditra.ticketservice.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCompletedEventHandler implements EventHandler<PaymentCompletedEvent> {
    
    private final TicketService ticketService;
    
    @Override
    @org.springframework.transaction.annotation.Transactional
    @KafkaListener(topics = "payment-completed", groupId = "ticket-service-group")
    public void handle(PaymentCompletedEvent event) {
        if (event == null || event.getTicketId() == null) {
            log.error("Invalid PaymentCompletedEvent received: {}", event);
            throw new IllegalArgumentException("Invalid event data: missing ticketId");
        }
        
        log.info("Handling payment completed event for ticket: {}", event.getTicketId());
        
        try {
            ticketService.updateTicketStatus(event.getTicketId(), TicketStatus.CONFIRMED);
            log.info("Ticket {} confirmed after successful payment", event.getTicketId());
        } catch (Exception e) {
            log.error("Failed to confirm ticket {} after payment", event.getTicketId(), e);
            throw e;
        }
    }
    
    @Override
    public Class<PaymentCompletedEvent> getEventType() {
        return PaymentCompletedEvent.class;
    }
}
