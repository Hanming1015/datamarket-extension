package com.synapse.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Emitted for any auditable action. Consumed by audit-service.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditEvent extends BaseEvent {

    private String userId;
    private String userName;
    private String action;
    private String datasetId;
    private String datasetName;
    private String details;
}
