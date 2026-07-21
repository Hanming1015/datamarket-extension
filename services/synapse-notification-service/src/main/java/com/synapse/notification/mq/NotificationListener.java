package com.synapse.notification.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.AccessEvent;
import com.synapse.common.event.PaymentEvent;
import com.synapse.notification.entity.Notification;
import com.synapse.notification.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通知消费者。单队列绑 access.# + payment.#,类级 {@code @RabbitListener} + 多 {@code @RabbitHandler}
 * 按事件类型分发:
 * <ul>
 *   <li>AccessEvent(APPROVED/REJECTED)→ 一条 APPROVAL 通知;</li>
 *   <li>PaymentEvent(PAID)→ 一条 PAYMENT 通知。</li>
 * </ul>
 * 幂等:按 (userId, refId, type) 查重后再插,避免至少一次投递造成重复通知。
 */
@Component
@RabbitListener(queues = MqConstants.NOTIFICATION_QUEUE)
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private static final String TYPE_APPROVAL = "APPROVAL";
    private static final String TYPE_PAYMENT = "PAYMENT";

    @Autowired
    private NotificationMapper notificationMapper;

    @RabbitHandler
    public void onAccessEvent(AccessEvent event) {
        String title;
        String content;
        if ("APPROVED".equals(event.getDecision())) {
            title = "访问申请已批准";
            content = "您对数据集「" + event.getDatasetName() + "」的访问申请已通过审批,请前往支付以生效。";
        } else {
            title = "访问申请被驳回";
            content = "您对数据集「" + event.getDatasetName() + "」的访问申请未通过审批。";
        }
        save(event.getRequesterId(), TYPE_APPROVAL, title, content, event.getAccessRequestId());
    }

    @RabbitHandler
    public void onPaymentEvent(PaymentEvent event) {
        save(event.getUserId(), TYPE_PAYMENT, "支付成功",
                "支付成功,数据访问授权已生效。", event.getAccessRequestId());
    }

    @RabbitHandler(isDefault = true)
    public void onOther(Object msg) {
        log.warn("notification received unhandled message type: {}", msg == null ? "null" : msg.getClass());
    }

    private void save(String userId, String type, String title, String content, String refId) {
        Long exists = notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", userId).eq("ref_id", refId).eq("type", type));
        if (exists != null && exists > 0) {
            log.info("notification skip duplicate user={} ref={} type={}", userId, refId, type);
            return;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(n);
        log.info("notification created user={} type={} ref={}", userId, type, refId);
    }
}
