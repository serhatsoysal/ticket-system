package com.heditra.events.sourcing;

import com.heditra.events.core.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventSourcingRepository<T extends AggregateRoot> {
    
    private final EventStore eventStore;
    
    public void save(T aggregate) {
        List<DomainEvent> uncommittedEvents = aggregate.getUncommittedEvents();
        
        if (!uncommittedEvents.isEmpty()) {
            eventStore.saveAll(uncommittedEvents);
            aggregate.clearUncommittedEvents();
        }
    }
    
    public T load(String aggregateId, Class<T> aggregateClass) {
        try {
            T aggregate = aggregateClass.getDeclaredConstructor().newInstance();
            List<DomainEvent> history = eventStore.getEvents(aggregateId);
            
            if (!history.isEmpty()) {
                aggregate.loadFromHistory(history);
            }
            
            return aggregate;
        } catch (Exception e) {
            log.error("Failed to load aggregate {}", aggregateId, e);
            throw new com.heditra.events.core.EventProcessingException("Failed to load aggregate: " + aggregateId, e);
        }
    }
}
