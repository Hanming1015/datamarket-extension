package com.synapse.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Emitted when a user-facing notification should be created.
 * Consumed by notification-service (fan-out from approval/payment events).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationEvent extends BaseEvent {

    private String userId;
    private String type;
    private String title;
    private String content;
    private String refId;
}
