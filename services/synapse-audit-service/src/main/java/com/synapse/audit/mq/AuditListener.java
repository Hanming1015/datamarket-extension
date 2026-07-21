package com.synapse.audit.mq;

import com.synapse.audit.entity.AuditLog;
import com.synapse.audit.mapper.AuditLogMapper;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.AccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计消费者:批准/驳回事件 -> 落一条审计日志。
 * <p>审计是<b>追加写日志</b>:采用 at-least-once 语义,重复投递可能多记一条——
 * 相对"涉钱必须幂等"的 billing,日志容忍偶发重复(实测极少发生)。
 * 抛异常 -> 重试耗尽后进 {@code synapse.audit.dlq}。
 */
@Component
public class AuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuditListener.class);

    @Autowired
    private AuditLogMapper auditLogMapper;

    @RabbitListener(queues = MqConstants.AUDIT_QUEUE)
    public void onAccessEvent(AccessEvent event) {
        AuditLog entry = new AuditLog();
        entry.setTimestamp(event.getOccurredAt() != null ? event.getOccurredAt() : LocalDateTime.now());
        entry.setUserId(event.getRequesterId());
        entry.setUserName(event.getRequesterName());
        entry.setAction("ACCESS_" + event.getDecision());
        entry.setDatasetId(event.getDatasetId());
        entry.setDatasetName(event.getDatasetName());
        entry.setDetails(buildDetails(event));
        auditLogMapper.insert(entry);

        log.info("audit logged action={} accessRequestId={} eventId={}",
                entry.getAction(), event.getAccessRequestId(), event.getEventId());
    }

    private String buildDetails(AccessEvent e) {
        return "accessRequestId=" + e.getAccessRequestId()
                + ", purpose=" + e.getPurpose()
                + ", consumerType=" + e.getConsumerType()
                + ", approver=" + e.getApproverId()
                + ", cost=" + e.getCost();
    }
}
