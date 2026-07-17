package com.synapse.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway auth settings, bound from {@code synapse.gateway.auth.*} (served by Nacos).
 * {@code whitelist} paths skip JWT verification (e.g. login / register).
 */
@Data
@ConfigurationProperties(prefix = "synapse.gateway.auth")
public class GatewayAuthProperties {

    private List<String> whitelist = new ArrayList<>();
}
