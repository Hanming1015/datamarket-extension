package com.synapse.billing.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.billing.entity.BillingRecord;
import com.synapse.billing.mapper.BillingRecordMapper;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.AccessEvent;
import com.synapse.common.event.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计费消费者。单队列绑两个路由键,用类级 {@code @RabbitListener} + 多个 {@code @RabbitHandler}
 * 按事件类型分发(靠消息头 {@code __TypeId__} 还原具体类型):
 * <ul>
 *   <li>{@code access.approved}(AccessEvent)→ 入账一条 UNPAID;</li>
 *   <li>{@code payment.succeeded}(PaymentEvent)→ 把对应账单对账为 PAID。</li>
 * </ul>
 * 两者都做幂等(涉钱),可安全承受 MQ 至少一次投递。
 */
@Component
@RabbitListener(queues = MqConstants.BILLING_QUEUE)
public class BillingListener {

    private static final Logger log = LoggerFactory.getLogger(BillingListener.class);

    @Autowired
    private BillingRecordMapper billingRecordMapper;

    @RabbitHandler
    public void onAccessApproved(AccessEvent event) {
        Long exists = billingRecordMapper.selectCount(new QueryWrapper<BillingRecord>()
                .eq("access_request_id", event.getAccessRequestId()));
        if (exists != null && exists > 0) {
            log.info("billing skip duplicate accessRequestId={} eventId={}",
                    event.getAccessRequestId(), event.getEventId());
            return;
        }

        BillingRecord r = new BillingRecord();
        r.setUserId(event.getRequesterId());
        r.setUserName(event.getRequesterName());
        r.setDatasetId(event.getDatasetId());
        r.setDatasetName(event.getDatasetName());
        r.setAccessRequestId(event.getAccessRequestId());
        r.setCost(event.getCost() != null ? event.getCost() : BigDecimal.ZERO);
        r.setDate(LocalDate.now());
        r.setCreatedAt(LocalDateTime.now());
        r.setPaymentStatus("UNPAID");
        billingRecordMapper.insert(r);

        log.info("billing recorded id={} accessRequestId={} cost={}",
                r.getId(), r.getAccessRequestId(), r.getCost());
    }

    @RabbitHandler
    public void onPaymentSucceeded(PaymentEvent event) {
        BillingRecord r = billingRecordMapper.selectOne(new QueryWrapper<BillingRecord>()
                .eq("access_request_id", event.getAccessRequestId()).last("limit 1"));
        if (r == null) {
            log.warn("billing no record to reconcile accessRequestId={}", event.getAccessRequestId());
            return;
        }
        if ("PAID".equals(r.getPaymentStatus())) {
            return;   // 幂等:已对账
        }
        r.setPaymentStatus("PAID");
        billingRecordMapper.updateById(r);
        log.info("billing reconciled PAID id={} accessRequestId={}", r.getId(), event.getAccessRequestId());
    }

    /** 兜底:未知类型消息不致命,记日志避免误入 DLQ 造成噪音。 */
    @RabbitHandler(isDefault = true)
    public void onOther(Object msg) {
        log.warn("billing received unhandled message type: {}", msg == null ? "null" : msg.getClass());
    }
}
