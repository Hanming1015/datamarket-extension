package com.synapse.common.config;

import com.synapse.common.exception.GlobalExceptionHandler;
import com.synapse.common.security.JwtProperties;
import com.synapse.common.security.JwtUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for synapse-common. Any service that depends on this module
 * gets {@link JwtUtil} and {@link GlobalExceptionHandler} registered automatically,
 * without needing to component-scan the com.synapse.common package.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
