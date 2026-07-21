package com.synapse.access.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表(outbox 模式),对应 {@code synapse_access.outbox_message}。
 * <p>核心:业务状态变更与"发事件的意图"写在<b>同一本地事务</b>——要么都成功、要么都回滚,
 * 消灭 3b 的 dual-write 缺口。真正投递 MQ 交给 {@link com.synapse.access.mq.OutboxRelay} 异步补偿。
 * <p>本服务只发 {@link com.synapse.common.event.AccessEvent},故 relay 反序列化类型固定,
 * {@code eventType} 直接存 AMQP 路由键(access.approved / access.rejected)。
 */
@Data
@TableName("outbox_message")
public class OutboxMessage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String aggregateType;   // AccessRequest
    private String aggregateId;     // access_requests.id

    /** 存 AMQP 路由键(如 access.approved),relay 据此发到 topic 交换机。 */
    private String eventType;

    /** 事件对象的 JSON。 */
    private String payload;

    /** PENDING / SENT / FAILED。 */
    private String status;

    private Integer retryCount;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
