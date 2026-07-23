package com.synapse.gateway.filter;

import com.synapse.common.constant.SecurityConstants;
import com.synapse.common.security.JwtUtil;
import com.synapse.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Central JWT pre-authentication for the whole cluster.
 * <p>
 * Whitelisted paths (login/register) pass through. Everything else must carry a valid
 * {@code Authorization: Bearer <jwt>}; the token is verified here <em>once</em>, and the
 * user id is injected as {@code X-User-Id} for downstream services to trust. Any client-supplied
 * {@code X-User-Id} is stripped first, so it cannot be spoofed.
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private GatewayAuthProperties authProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // CORS 预检:OPTIONS 不带 token,交给全局 CORS 过滤器应答,不做 JWT 校验
        if (request.getMethod() == org.springframework.http.HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // 白名单:剥掉客户端可能伪造的身份头后直接放行
        if (isWhitelisted(path)) {
            return chain.filter(stripUserHeader(exchange));
        }

        // 取 Bearer token
        String header = request.getHeaders().getFirst(SecurityConstants.AUTH_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return unauthorized(exchange, "missing token");
        }
        String token = header.substring(SecurityConstants.TOKEN_PREFIX.length());

        String userId;
        try {
            Claims claims = jwtUtil.parse(token);   // 验签 + 验过期,失败抛异常
            userId = claims.getSubject();
        } catch (Exception e) {
            log.warn("reject {}: invalid token ({})", path, e.getMessage());
            return unauthorized(exchange, "invalid token");
        }

        // 关键:set 覆盖下游身份头(而非 add),客户端传的同名头被顶掉,无法伪造
        ServerHttpRequest mutated = request.mutate()
                .headers(h -> h.set(SecurityConstants.USER_ID_HEADER, userId))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : authProperties.getWhitelist()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private ServerWebExchange stripUserHeader(ServerWebExchange exchange) {
        ServerHttpRequest cleaned = exchange.getRequest().mutate()
                .headers(h -> h.remove(SecurityConstants.USER_ID_HEADER))
                .build();
        return exchange.mutate().request(cleaned).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        String body = "{\"code\":401,\"message\":\"" + reason + "\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -100;   // 尽早执行,先于路由转发
    }
}
