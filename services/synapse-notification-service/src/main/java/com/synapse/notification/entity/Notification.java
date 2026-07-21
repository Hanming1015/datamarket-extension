package com.synapse.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知,对应 {@code synapse_notification.notification}。由 MQ 消费者从领域事件生成。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /** APPROVAL / PAYMENT。 */
    private String type;

    private String title;
    private String content;

    /** 关联业务 id(此处用 accessRequestId,便于前端跳转)。 */
    private String refId;

    private Boolean isRead;

    private LocalDateTime createdAt;
}
