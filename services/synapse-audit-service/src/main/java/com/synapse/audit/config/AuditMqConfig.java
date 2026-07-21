package com.synapse.audit.config;

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
 * audit-service 的 MQ 拓扑。业务队列 {@code synapse.audit.queue} 绑通配 {@code access.#}
 * —— 批准与驳回都记审计(这正是 topic 扇出的用处:同一条 access.approved 也被 billing 队列收到一份)。
 * 死信同 billing:重试耗尽 -> DLX -> {@code synapse.audit.dlq}。
 */
@Configuration
public class AuditMqConfig {

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(MqConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(MqConstants.AUDIT_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.AUDIT_DLQ)
                .build();
    }

    @Bean
    public Queue auditDlq() {
        return QueueBuilder.durable(MqConstants.AUDIT_DLQ).build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(eventExchange()).with(MqConstants.RK_ALL_ACCESS);
    }

    @Bean
    public Binding auditDlqBinding() {
        return BindingBuilder.bind(auditDlq()).to(dlxExchange()).with(MqConstants.AUDIT_DLQ);
    }
}
