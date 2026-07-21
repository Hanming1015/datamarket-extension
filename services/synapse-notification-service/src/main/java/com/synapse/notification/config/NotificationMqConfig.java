package com.synapse.notification.config;

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
 * notification-service 的 MQ 拓扑。业务队列 {@code synapse.notification.queue}
 * 同时绑 {@code access.#} 与 {@code payment.#} —— 审批(批准/驳回)和支付成功都要通知消费者。
 * 死信同其它服务:重试耗尽 -> DLX -> {@code synapse.notification.dlq}。
 */
@Configuration
public class NotificationMqConfig {

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(MqConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(MqConstants.NOTIFICATION_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(MqConstants.NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding notificationAccessBinding() {
        return BindingBuilder.bind(notificationQueue()).to(eventExchange()).with(MqConstants.RK_ALL_ACCESS);
    }

    @Bean
    public Binding notificationPaymentBinding() {
        return BindingBuilder.bind(notificationQueue()).to(eventExchange()).with(MqConstants.RK_ALL_PAYMENT);
    }

    @Bean
    public Binding notificationDlqBinding() {
        return BindingBuilder.bind(notificationDlq()).to(dlxExchange()).with(MqConstants.NOTIFICATION_DLQ);
    }
}
