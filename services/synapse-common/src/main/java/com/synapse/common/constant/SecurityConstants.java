package com.synapse.common.constant;

/**
 * Shared security-related header/token constants.
 */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    /** Standard bearer-token header. */
    public static final String AUTH_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Headers the gateway injects downstream after it has verified the JWT. */
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
}
