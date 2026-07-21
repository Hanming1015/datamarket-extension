package com.synapse.billing.config;

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
 * billing-service 的 MQ 拓扑(消费者侧自己拥有队列 + 绑定 + 死信)。
 * <ul>
 *   <li>业务队列 {@code synapse.billing.queue} 绑 {@code access.approved} —— 只有批准才入账;</li>
 *   <li>队列声明死信路由:消费重试耗尽后,消息被投到 DLX -> {@code synapse.billing.dlq},
 *       可在 RabbitMQ 控制台(15672)查看毒消息,不阻塞后续。</li>
 * </ul>
 * 交换机用 durable,与生产者声明一致(幂等)。
 */
@Configuration
public class BillingMqConfig {

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(MqConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue billingQueue() {
        return QueueBuilder.durable(MqConstants.BILLING_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.BILLING_DLQ)
                .build();
    }

    @Bean
    public Queue billingDlq() {
        return QueueBuilder.durable(MqConstants.BILLING_DLQ).build();
    }

    @Bean
    public Binding billingBinding() {
        return BindingBuilder.bind(billingQueue()).to(eventExchange()).with(MqConstants.RK_ACCESS_APPROVED);
    }

    @Bean
    public Binding billingDlqBinding() {
        return BindingBuilder.bind(billingDlq()).to(dlxExchange()).with(MqConstants.BILLING_DLQ);
    }
}
