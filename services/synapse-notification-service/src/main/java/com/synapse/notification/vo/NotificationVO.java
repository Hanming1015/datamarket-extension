package com.synapse.notification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 通知展示对象。 */
@Data
public class NotificationVO {

    private String id;
    private String userId;
    private String type;
    private String title;
    private String content;
    private String refId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
