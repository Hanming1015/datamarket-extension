package com.synapse.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT helper using the modern jjwt 0.12.x API.
 * Wire-compatible with the monolith: same HMAC key bytes, HS256, subject = userId,
 * issuer = "sg" — so tokens are interchangeable across old and new systems.
 * Instantiated as a bean by {@code CommonAutoConfiguration} from {@link JwtProperties}.
 */
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMillis;
    private final String issuer;

    public JwtUtil(JwtProperties properties) {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.ttlMillis = properties.getTtlMillis();
        this.issuer = properties.getIssuer();
    }

    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** Create a token whose subject is the user id. */
    public String createToken(String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(uuid())
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Parse and verify a token, returning its claims (throws if invalid/expired). */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Convenience: extract the user id (subject) from a token. */
    public String getUserId(String token) {
        return parse(token).getSubject();
    }
}
