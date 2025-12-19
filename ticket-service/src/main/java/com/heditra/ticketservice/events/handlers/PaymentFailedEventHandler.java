package com.heditra.ticketservice.events.handlers;

import com.heditra.events.core.EventHandler;
import com.heditra.events.payment.PaymentFailedEvent;
import com.heditra.ticketservice.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedEventHandler implements EventHandler<PaymentFailedEvent> {
    
    private final TicketService ticketService;
    
    @Override
    @org.springframework.transaction.annotation.Transactional
    @KafkaListener(topics = "payment-failed", groupId = "ticket-service-group")
    public void handle(PaymentFailedEvent event) {
        if (event == null || event.getTicketId() == null) {
            log.error("Invalid PaymentFailedEvent received: {}", event);
            throw new IllegalArgumentException("Invalid event data: missing ticketId");
        }
        
        log.info("Handling payment failed event for ticket: {}", event.getTicketId());
        
        try {
            ticketService.cancelTicket(event.getTicketId());
            log.info("Ticket {} cancelled after failed payment", event.getTicketId());
        } catch (Exception e) {
            log.error("Failed to cancel ticket {} after payment failure", event.getTicketId(), e);
            throw e;
        }
    }
    
    @Override
    public Class<PaymentFailedEvent> getEventType() {
        return PaymentFailedEvent.class;
    }
}
