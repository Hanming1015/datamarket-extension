package com.synapse.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;

/**
 * 共享的 RabbitMQ 消息序列化配置(Phase 3b)。
 * 生产者(access)与消费者(billing/audit)引同一个 JSON 转换器,保证:
 * <ul>
 *   <li>事件以 JSON 传输,{@code __TypeId__} 头携带类名 -> 消费端还原具体事件类型;</li>
 *   <li>{@link JavaTimeModule} 让 {@code LocalDateTime}(occurredAt 等)正常读写,不落成时间戳数组;</li>
 *   <li>反序列化只信任 {@code com.synapse.common.event} 包,避免任意类实例化。</li>
 * </ul>
 * 仅在 classpath 有 {@link RabbitTemplate}(即引了 amqp starter)的服务里生效;
 * Spring Boot 的 RabbitTemplate 与 @RabbitListener 容器工厂会自动采用此 {@link MessageConverter} Bean。
 */
@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitSupportConfig {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(WRITE_DATES_AS_TIMESTAMPS);

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);

        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.synapse.common.event");
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}
