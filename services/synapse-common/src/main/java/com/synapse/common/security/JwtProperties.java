package com.synapse.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized JWT settings (bind to {@code synapse.jwt.*}).
 * Defaults intentionally preserve the monolith's key/issuer/TTL so tokens issued
 * by the old system remain valid during migration. Override the secret in prod.
 */
@Data
@ConfigurationProperties(prefix = "synapse.jwt")
public class JwtProperties {

    /** HMAC secret; must be >= 32 bytes for HS256. Default = monolith key (rotate in prod). */
    private String secret = "SDFGjhdsfalshdfHFdsjkdsfds121232131afasdfac";

    /** Token time-to-live in milliseconds. Default = 14 days (matches monolith). */
    private long ttlMillis = 60L * 60 * 1000 * 24 * 14;

    /** Issuer claim. Default = "sg" (matches monolith). */
    private String issuer = "sg";
}
