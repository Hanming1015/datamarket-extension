package com.synapse.access.mq;

import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.AccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 审批事件的 MQ 出口。业务方(AccessServiceImpl)在 @Transactional 方法里
 * {@code publishEvent(AccessEvent)},本监听器在 <b>事务提交之后</b> 才真正投递到 RabbitMQ:
 * <ul>
 *   <li>先落库、后发消息 —— 避免"事务回滚了消息却发出去"的假事件;</li>
 *   <li>但这是 3b 的<b>朴素方案</b>:提交已成功、若此处投递失败,消息就丢了(dual-write 缺口)。
 *       故此处仅 catch+log 不抛(抛也无法回滚已提交的事务)。3c 用本地消息表(outbox)补上这个洞。</li>
 * </ul>
 */
@Component
public class AccessEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccessEventPublisher.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessDecision(AccessEvent event) {
        String routingKey = "APPROVED".equals(event.getDecision())
                ? MqConstants.RK_ACCESS_APPROVED
                : MqConstants.RK_ACCESS_REJECTED;
        try {
            rabbitTemplate.convertAndSend(MqConstants.EVENT_EXCHANGE, routingKey, event);
            log.info("published {} eventId={} accessRequestId={}",
                    routingKey, event.getEventId(), event.getAccessRequestId());
        } catch (Exception e) {
            // 3b 朴素方案的已知缺口:提交后投递失败即丢事件。3c outbox 修复。
            log.error("failed to publish access event (LOST until 3c outbox) accessRequestId={} rk={}",
                    event.getAccessRequestId(), routingKey, e);
        }
    }
}
