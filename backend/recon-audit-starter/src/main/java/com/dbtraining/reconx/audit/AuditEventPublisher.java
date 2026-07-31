package com.dbtraining.reconx.audit;

import org.springframework.context.ApplicationEventPublisher;

public class AuditEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final AuditProperties properties;

    public AuditEventPublisher(ApplicationEventPublisher publisher, AuditProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}