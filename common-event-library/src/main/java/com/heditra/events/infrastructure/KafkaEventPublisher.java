package com.heditra.events.infrastructure;

import com.heditra.events.core.DomainEvent;
import com.heditra.events.core.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Override
    public <T extends DomainEvent> CompletableFuture<Void> publish(T event) {
        String topic = deriveTopicFromEvent(event);
        return publish(topic, event);
    }
    
    @Override
    public <T extends DomainEvent> CompletableFuture<Void> publish(String topic, T event) {
        if (event == null) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Event cannot be null"));
            return failed;
        }
        
        if (event.getAggregateId() == null) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Event aggregateId cannot be null"));
            return failed;
        }
        
        CompletableFuture<Void> resultFuture = new CompletableFuture<>();
        
        kafkaTemplate.send(topic, event.getAggregateId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        resultFuture.complete(null);
                    } else {
                        log.error("Failed to publish event {} to topic {}", event.getEventType(), topic, ex);
                        resultFuture.completeExceptionally(new RuntimeException("Event publishing failed", ex));
                    }
                });
        
        return resultFuture;
    }
    
    private <T extends DomainEvent> String deriveTopicFromEvent(T event) {
        return event.getEventType().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }
}
