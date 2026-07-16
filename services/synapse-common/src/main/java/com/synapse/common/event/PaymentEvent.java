package com.synapse.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Emitted by payment-service when a payment order changes state (e.g. PAID).
 * Consumed by billing-service (reconcile) and notification-service.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentEvent extends BaseEvent {

    private String paymentOrderId;
    private String accessRequestId;
    private String billingRecordId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String status;
}
