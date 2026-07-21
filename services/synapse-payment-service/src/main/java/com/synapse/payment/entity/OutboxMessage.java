package com.synapse.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表,对应 {@code synapse_payment.outbox_message}。
 * 与 payment_order 的状态更新同事务写入,relay 异步投递 —— 保证"标 PAID"与"发 payment.succeeded"最终一致。
 * 本服务只发 {@link com.synapse.common.event.PaymentEvent},{@code eventType} 存 AMQP 路由键。
 */
@Data
@TableName("outbox_message")
public class OutboxMessage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String aggregateType;   // PaymentOrder
    private String aggregateId;     // payment_order.id
    private String eventType;       // 路由键 payment.succeeded
    private String payload;         // PaymentEvent JSON
    private String status;          // PENDING / SENT / FAILED
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
