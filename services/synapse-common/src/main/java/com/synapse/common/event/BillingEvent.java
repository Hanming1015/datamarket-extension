package com.synapse.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Emitted when an access request is approved and billing should be recorded.
 * Consumed by billing-service.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BillingEvent extends BaseEvent {

    private String accessRequestId;
    private String userId;
    private String userName;
    private String datasetId;
    private String datasetName;
    private BigDecimal cost;
}
