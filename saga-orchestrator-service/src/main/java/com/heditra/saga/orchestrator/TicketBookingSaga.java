package com.heditra.saga.orchestrator;

import com.heditra.events.core.EventPublisher;
import com.heditra.events.ticket.TicketCreatedEvent;
import com.heditra.saga.compensation.CompensationHandler;
import com.heditra.saga.exception.BusinessException;
import com.heditra.saga.model.SagaInstance;
import com.heditra.saga.model.SagaStatus;
import com.heditra.saga.model.SagaStep;
import com.heditra.saga.model.StepStatus;
import com.heditra.saga.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketBookingSaga {
    
    private final SagaInstanceRepository sagaRepository;
    private final EventPublisher eventPublisher;
    private final CompensationHandler compensationHandler;
    
    @KafkaListener(topics = "ticket-created", groupId = "saga-orchestrator-group")
    @Transactional
    public void onTicketCreated(TicketCreatedEvent event) {
        if (event == null || event.getTicketId() == null) {
            log.error("Invalid TicketCreatedEvent received: {}", event);
            throw new IllegalArgumentException("Invalid event data: missing ticketId");
        }
        
        log.info("Starting ticket booking saga for ticket: {}", event.getTicketId());
        
        SagaInstance saga = SagaInstance.builder()
                .sagaId(UUID.randomUUID().toString())
                .ticketId(event.getTicketId())
                .status(SagaStatus.STARTED)
                .startedAt(LocalDateTime.now())
                .build();
        
        sagaRepository.save(saga);
        
        try {
            executeStep(saga, "inventory-reservation", () -> true);
            executeStep(saga, "payment-initiation", () -> true);
            executeStep(saga, "notification-sending", () -> true);
            
            saga.setStatus(SagaStatus.IN_PROGRESS);
            sagaRepository.save(saga);
            
            log.info("Ticket booking saga in progress for ticket: {}", event.getTicketId());
            
        } catch (Exception e) {
            log.error("Saga execution failed for ticket: {}", event.getTicketId(), e);
            compensate(saga, e.getMessage());
        }
    }
    
    private void executeStep(SagaInstance saga, String stepName, StepExecutor executor) {
        SagaStep step = SagaStep.builder()
                .stepName(stepName)
                .status(StepStatus.IN_PROGRESS)
                .executedAt(LocalDateTime.now())
                .build();
        
        saga.addStep(step);
        
        try {
            boolean success = executor.execute();
            
            if (success) {
                step.setStatus(StepStatus.COMPLETED);
            } else {
                step.setStatus(StepStatus.FAILED);
                throw new BusinessException("Step execution returned false: " + stepName, "SAGA_STEP_FAILED");
            }
            
        } catch (Exception e) {
            step.setStatus(StepStatus.FAILED);
            step.setErrorMessage(e.getMessage());
            log.error("Saga step failed: {}", stepName, e);
            throw e;
        }
    }
    
    private void compensate(SagaInstance saga, String reason) {
        log.warn("Compensating saga: {} due to: {}", saga.getSagaId(), reason);
        
        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setCompensationReason(reason);
        sagaRepository.save(saga);
        
        try {
            compensationHandler.compensate(saga);
            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setCompletedAt(LocalDateTime.now());
            log.info("Saga compensated successfully: {}", saga.getSagaId());
        } catch (Exception e) {
            saga.setStatus(SagaStatus.FAILED);
            log.error("Saga compensation failed: {}", saga.getSagaId(), e);
        }
        
        sagaRepository.save(saga);
    }
    
    @FunctionalInterface
    private interface StepExecutor {
        boolean execute();
    }
}
