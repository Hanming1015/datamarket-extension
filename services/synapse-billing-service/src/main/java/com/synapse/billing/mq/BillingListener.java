package com.synapse.billing.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.billing.entity.BillingRecord;
import com.synapse.billing.mapper.BillingRecordMapper;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.AccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计费消费者:批准事件 -> 入账一条 UNPAID 账单。
 * <p><b>幂等(涉钱必须做)</b>:MQ 至少一次投递,同一 accessRequestId 可能重复到达;
 * 入账前先按 accessRequestId 查重,已入账则跳过——保证不重复扣费。
 * <p>抛异常 -> 容器重试(见 Nacos 配置的 retry),耗尽后进 {@code synapse.billing.dlq}。
 */
@Component
public class BillingListener {

    private static final Logger log = LoggerFactory.getLogger(BillingListener.class);

    @Autowired
    private BillingRecordMapper billingRecordMapper;

    @RabbitListener(queues = MqConstants.BILLING_QUEUE)
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
}
