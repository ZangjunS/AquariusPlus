package com.ds.gateway.filter;

import com.ds.common.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 网关全局过滤器：
 *   1) 白名单路径直接放行
 *   2) 其它路径解析 Authorization 头里的 JWT，失败返回 401
 *   3) 把 userId/username/role 写进下游请求头（真正替换 exchange 中的请求）
 *   4) 用 Resilience4j RateLimiter 做简单限流（全局实例，每秒 100 次）
 */
@Slf4j
@Component
public class GatewayGlobalFilter implements
        org.springframework.cloud.gateway.filter.GlobalFilter,
        Ordered {

    private final JwtUtil jwtUtil;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final ObjectMapper om = new ObjectMapper();

    public GatewayGlobalFilter(JwtUtil jwtUtil, RateLimiterRegistry rateLimiterRegistry) {
        this.jwtUtil = jwtUtil;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isWhitelist(path)) {
            if (!allow()) return tooManyRequests(exchange);
            return chain.filter(exchange);
        }
        ServerHttpRequest authed = authenticate(exchange);
        if (authed == null) {
            return unauthorized(exchange);
        }
        if (!allow()) return tooManyRequests(exchange);
        return chain.filter(exchange.mutate().request(authed).build());
    }

    /** 返回注入了用户头的请求；鉴权失败返回 null（调用方据此写 401） */
    private ServerHttpRequest authenticate(ServerWebExchange exchange) {
        ServerHttpRequest req = exchange.getRequest();
        String token = extract(req);
        if (token == null) {
            log.warn("[jwt] missing token on {}", req.getPath());
            return null;
        }
        try {
            Claims c = jwtUtil.parse(token);
            return req.mutate()
                    .header("X-User-Id", c.getSubject())
                    .header("X-User-Name", String.valueOf(c.get("username")))
                    .header("X-User-Role", String.valueOf(c.get("role")))
                    .build();
        } catch (JwtException e) {
            log.warn("[jwt] invalid token on {}: {}", req.getPath(), e.getMessage());
            return null;
        }
    }

    private String extract(ServerHttpRequest req) {
        String t = req.getHeaders().getFirst("Authorization");
        if (t == null || t.isBlank()) return null;
        return t.startsWith("Bearer ") ? t.substring(7).trim() : t.trim();
    }

    private boolean isWhitelist(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/doc.html")
                || path.startsWith("/webjars")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/fallback")
                || path.contains("/auth/login")
                || path.contains("/auth/register")
                || path.contains("/auth/verify")
                || path.contains("/auth/me");
    }

    private boolean allow() {
        RateLimiter rl = rateLimiterRegistry.rateLimiter("gateway");
        boolean ok = rl.acquirePermission();
        if (!ok) log.warn("[ratelimit] rejected request");
        return ok;
    }

    private Mono<Void> unauthorized(ServerWebExchange ex) {
        return writeJson(ex, 401, "未认证或令牌无效");
    }

    private Mono<Void> tooManyRequests(ServerWebExchange ex) {
        return writeJson(ex, 429, "请求过于频繁，请稍后再试");
    }

    /** 注意：Map.of 不接受 null 值，这里用 HashMap 避免 NPE（否则 401 会变成 500） */
    private Mono<Void> writeJson(ServerWebExchange ex, int status, String message) {
        ServerHttpResponse resp = ex.getResponse();
        resp.setStatusCode(HttpStatus.valueOf(status));
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("code", status);
        body.put("message", message);
        body.put("data", null);
        byte[] bytes;
        try {
            bytes = om.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = ("{\"code\":" + status + "}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buf = resp.bufferFactory().wrap(bytes);
        return resp.writeWith(Mono.just(buf));
    }
}
