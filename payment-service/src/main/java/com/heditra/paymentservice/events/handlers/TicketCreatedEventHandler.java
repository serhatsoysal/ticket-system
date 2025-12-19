package com.heditra.paymentservice.events.handlers;

import com.heditra.events.core.EventHandler;
import com.heditra.events.ticket.TicketCreatedEvent;
import com.heditra.paymentservice.model.Payment;
import com.heditra.paymentservice.model.PaymentMethod;
import com.heditra.paymentservice.model.PaymentStatus;
import com.heditra.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketCreatedEventHandler implements EventHandler<TicketCreatedEvent> {
    
    private final PaymentRepository paymentRepository;
    
    @Override
    @org.springframework.transaction.annotation.Transactional
    @KafkaListener(topics = "ticket-created", groupId = "payment-service-group")
    public void handle(TicketCreatedEvent event) {
        if (event == null || event.getTicketId() == null || event.getUserId() == null || event.getTotalAmount() == null) {
            log.error("Invalid TicketCreatedEvent received: {}", event);
            throw new IllegalArgumentException("Invalid event data: missing required fields");
        }
        
        log.info("Handling ticket created event for ticket: {}", event.getTicketId());
        
        try {
            Payment payment = new Payment();
            payment.setTicketId(event.getTicketId());
            payment.setUserId(event.getUserId());
            payment.setAmount(event.getTotalAmount());
            payment.setPaymentMethod(PaymentMethod.CREDIT_CARD);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setCreatedAt(LocalDateTime.now());
            
            paymentRepository.save(payment);
            log.info("Payment record created for ticket: {}", event.getTicketId());
        } catch (Exception e) {
            log.error("Failed to create payment record for ticket: {}", event.getTicketId(), e);
            throw e;
        }
    }
    
    @Override
    public Class<TicketCreatedEvent> getEventType() {
        return TicketCreatedEvent.class;
    }
}
