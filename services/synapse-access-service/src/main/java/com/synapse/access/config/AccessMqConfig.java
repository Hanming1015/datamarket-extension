package com.synapse.access.config;

import com.synapse.common.constant.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * access-service 的 MQ 拓扑。
 * <p>生产者侧:声明扇出用的 topic 交换机(outbox relay 发 access.approved / access.rejected 到这里)。
 * <p>消费者侧(3c 新增):access 自己的队列绑 {@code payment.succeeded},
 * 收到后把申请从 PENDING_PAYMENT 推进到 GRANTED;死信落 {@code synapse.access.dlq}。
 */
@Configuration
public class AccessMqConfig {

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(MqConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue accessQueue() {
        return QueueBuilder.durable(MqConstants.ACCESS_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.ACCESS_DLQ)
                .build();
    }

    @Bean
    public Queue accessDlq() {
        return QueueBuilder.durable(MqConstants.ACCESS_DLQ).build();
    }

    @Bean
    public Binding accessPaymentBinding() {
        return BindingBuilder.bind(accessQueue()).to(eventExchange()).with(MqConstants.RK_PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding accessDlqBinding() {
        return BindingBuilder.bind(accessDlq()).to(dlxExchange()).with(MqConstants.ACCESS_DLQ);
    }
}
