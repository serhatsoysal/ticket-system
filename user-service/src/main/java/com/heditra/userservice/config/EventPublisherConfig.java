package com.heditra.userservice.config;

import com.heditra.events.core.EventPublisher;
import com.heditra.events.infrastructure.KafkaEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class EventPublisherConfig {
    
    @Bean
    public EventPublisher eventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        return new KafkaEventPublisher(kafkaTemplate);
    }
}
