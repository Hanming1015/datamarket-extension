package com.synapse.access.config;

import com.synapse.common.constant.MqConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * access-service 作为生产者,只需声明扇出用的 topic 交换机(durable)。
 * 队列与绑定由各消费者(billing/audit)各自拥有并声明——生产者不该知道谁在消费。
 * 交换机声明是幂等的:即使消费者也声明同名交换机,不冲突。
 */
@Configuration
public class AccessMqConfig {

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(MqConstants.EVENT_EXCHANGE, true, false);
    }
}
