package com.datamarket.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class for Cors.
 */

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Paths allowed for cross-origin access: all paths
                .allowedOriginPatterns("*") // Origins allowed for access: all origins (recommended syntax for Spring Boot 2.4+)
                .allowedMethods("GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH") // Allowed request methods
                .allowCredentials(true) // Whether to allow sending Cookies/Tokens
                .allowedHeaders("*") // Allowed Headers
                .maxAge(3600); // Preflight request cache duration (seconds)
    }
}