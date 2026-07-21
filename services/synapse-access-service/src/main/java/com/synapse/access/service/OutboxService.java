package com.synapse.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.access.entity.OutboxMessage;
import com.synapse.access.mapper.OutboxMessageMapper;
import com.synapse.common.event.AccessEvent;
import com.synapse.common.exception.BusinessException;
import com.synapse.common.api.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * outbox 写入端:在业务事务内落一条 PENDING 消息。
 * 必须与业务表更新同处一个 {@code @Transactional} 边界——这正是 outbox 的意义所在。
 */
@Service
public class OutboxService {

    @Autowired
    private OutboxMessageMapper outboxMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 记录一条待发事件(不真正投递,交给 relay)。
     *
     * @param routingKey AMQP 路由键(access.approved / access.rejected),存进 event_type
     * @param event      要发布的事件对象
     */
    public void record(String routingKey, AccessEvent event) {
        OutboxMessage msg = new OutboxMessage();
        msg.setAggregateType("AccessRequest");
        msg.setAggregateId(event.getAccessRequestId());
        msg.setEventType(routingKey);
        try {
            msg.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            // 序列化失败属编程错误,直接让业务事务回滚
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "outbox serialize failed");
        }
        msg.setStatus("PENDING");
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());
        outboxMessageMapper.insert(msg);
    }
}
