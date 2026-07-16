package com.synapse.common.event;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base type for all domain events published to RabbitMQ.
 * Carries an id + timestamp for idempotency/auditing on the consumer side.
 */
@Data
public abstract class BaseEvent implements Serializable {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt = LocalDateTime.now();
}
