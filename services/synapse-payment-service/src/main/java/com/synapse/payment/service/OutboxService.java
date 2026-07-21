package com.synapse.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.common.api.ResultCode;
import com.synapse.common.event.PaymentEvent;
import com.synapse.common.exception.BusinessException;
import com.synapse.payment.entity.OutboxMessage;
import com.synapse.payment.mapper.OutboxMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * outbox 写入端:在标 PAID 的同一事务里落一条 PENDING 消息。
 */
@Service
public class OutboxService {

    @Autowired
    private OutboxMessageMapper outboxMessageMapper;

    @Autowired
    private ObjectMapper objectMapper;

    public void record(String routingKey, PaymentEvent event) {
        OutboxMessage msg = new OutboxMessage();
        msg.setAggregateType("PaymentOrder");
        msg.setAggregateId(event.getPaymentOrderId());
        msg.setEventType(routingKey);
        try {
            msg.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "outbox serialize failed");
        }
        msg.setStatus("PENDING");
        msg.setRetryCount(0);
        msg.setCreatedAt(LocalDateTime.now());
        outboxMessageMapper.insert(msg);
    }
}
