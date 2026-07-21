package com.synapse.payment.config;

import com.synapse.common.constant.MqConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * payment-service 在 c1 里只是生产者(outbox relay 发 payment.succeeded),
 * 故只声明扇出交换机;它自己不消费任何队列。
 */
@Configuration
public class PaymentMqConfig {

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(MqConstants.EVENT_EXCHANGE, true, false);
    }
}
