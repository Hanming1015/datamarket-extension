package com.synapse.common.constant;

/**
 * RabbitMQ topology constants shared by producers and consumers.
 * A single topic exchange fans domain events out to per-service queues;
 * a dead-letter exchange backs failed deliveries. Bindings are declared in Phase 3.
 */
public final class MqConstants {

    private MqConstants() {
    }

    /** Topic exchange for domain-event fan-out. */
    public static final String EVENT_EXCHANGE = "synapse.event.exchange";
    /** Dead-letter exchange for failed messages. */
    public static final String DLX_EXCHANGE = "synapse.dlx.exchange";

    // ---- per-consumer queues ----
    public static final String BILLING_QUEUE = "synapse.billing.queue";
    public static final String AUDIT_QUEUE = "synapse.audit.queue";
    public static final String NOTIFICATION_QUEUE = "synapse.notification.queue";
    public static final String PAYMENT_QUEUE = "synapse.payment.queue";
    /** access 作为消费者:收 payment.succeeded 把 PENDING_PAYMENT 推进到 GRANTED(Phase 3c)。 */
    public static final String ACCESS_QUEUE = "synapse.access.queue";

    // ---- dead-letter queues ----
    public static final String BILLING_DLQ = "synapse.billing.dlq";
    public static final String AUDIT_DLQ = "synapse.audit.dlq";
    public static final String NOTIFICATION_DLQ = "synapse.notification.dlq";
    public static final String ACCESS_DLQ = "synapse.access.dlq";

    // ---- routing keys (topic) ----
    public static final String RK_ACCESS_APPROVED = "access.approved";
    public static final String RK_ACCESS_REJECTED = "access.rejected";
    public static final String RK_PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String RK_BILLING_CREATED = "billing.created";

    // ---- wildcard binding patterns ----
    public static final String RK_ALL_ACCESS = "access.#";
    public static final String RK_ALL_PAYMENT = "payment.#";
}
