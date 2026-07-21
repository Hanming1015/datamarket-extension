package com.synapse.payment.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.PaymentEvent;
import com.synapse.payment.entity.OutboxMessage;
import com.synapse.payment.mapper.OutboxMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * outbox 投递端:定时把 PENDING 的 payment.succeeded 发到 MQ,成功标 SENT。
 * 即使标 PAID 提交后进程崩溃,重启 relay 补发,消息不丢(消费端幂等)。
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 50;
    private static final int MAX_RETRY = 5;

    @Autowired
    private OutboxMessageMapper outboxMessageMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${synapse.payment.outbox-relay-ms:2000}")
    public void flush() {
        List<OutboxMessage> pending = outboxMessageMapper.selectList(
                new QueryWrapper<OutboxMessage>().eq("status", "PENDING")
                        .orderByAsc("created_at").last("limit " + BATCH));
        for (OutboxMessage msg : pending) {
            try {
                PaymentEvent event = objectMapper.readValue(msg.getPayload(), PaymentEvent.class);
                rabbitTemplate.convertAndSend(MqConstants.EVENT_EXCHANGE, msg.getEventType(), event);
                msg.setStatus("SENT");
                msg.setSentAt(LocalDateTime.now());
                outboxMessageMapper.updateById(msg);
                log.info("outbox sent id={} rk={} paymentOrderId={}",
                        msg.getId(), msg.getEventType(), msg.getAggregateId());
            } catch (Exception e) {
                int retry = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;
                msg.setRetryCount(retry);
                if (retry >= MAX_RETRY) {
                    msg.setStatus("FAILED");
                    log.error("outbox giving up id={} after {} tries", msg.getId(), retry, e);
                } else {
                    log.warn("outbox retry id={} attempt={}", msg.getId(), retry, e);
                }
                outboxMessageMapper.updateById(msg);
            }
        }
    }
}
