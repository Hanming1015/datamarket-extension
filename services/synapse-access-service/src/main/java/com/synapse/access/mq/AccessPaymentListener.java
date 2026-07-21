package com.synapse.access.mq;

import com.synapse.access.service.AccessService;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * access 作为消费者(3c):收 {@code payment.succeeded},把对应申请推进到 GRANTED。
 * 幂等在 {@link AccessService#markGrantedByPayment} 内(已 GRANTED 则跳过),可安全承受重复投递。
 */
@Component
public class AccessPaymentListener {

    private static final Logger log = LoggerFactory.getLogger(AccessPaymentListener.class);

    @Autowired
    private AccessService accessService;

    @RabbitListener(queues = MqConstants.ACCESS_QUEUE)
    public void onPaymentSucceeded(PaymentEvent event) {
        log.info("access received payment.succeeded accessRequestId={} eventId={}",
                event.getAccessRequestId(), event.getEventId());
        accessService.markGrantedByPayment(event.getAccessRequestId());
    }
}
