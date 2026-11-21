package com.ds.gateway.security;

import com.ds.gateway.filter.GatewayGlobalFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 解析 Authorization 头里的 JWT；成功后把 userId/username/role 写进下游请求头；
 * 失败交给 GatewayGlobalFilter 返回 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter {

    private final com.ds.common.security.JwtUtil jwtUtil;

    /** @return Mono 完成 = 校验通过；Mono.error = 不通过 */
    public Mono<Void> check(ServerWebExchange exchange) {
        ServerHttpRequest req = exchange.getRequest();
        String token = extract(req);
        if (token == null) {
            log.warn("[jwt] missing token on {}", req.getPath());
            return exchange.getResponse().writeWith(Mono.empty()).then(Mono.error(new NoTokenException()));
        }
        try {
            Claims c = jwtUtil.parse(token);
            // 透传用户身份给下游
            ServerHttpRequest mutated = req.mutate()
                    .header("X-User-Id", c.getSubject())
                    .header("X-User-Name", String.valueOf(c.get("username")))
                    .header("X-User-Role", String.valueOf(c.get("role")))
                    .build();
            exchange.getAttributes().put("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_TEMPLATE",
                    req.getURI());
            // 替换请求
            exchange.getRequest().mutate();
            return Mono.empty();
        } catch (JwtException e) {
            log.warn("[jwt] invalid token on {}: {}", req.getPath(), e.getMessage());
            return exchange.getResponse().writeWith(Mono.empty()).then(Mono.error(new InvalidTokenException(e)));
        }
    }

    private String extract(ServerHttpRequest req) {
        String t = req.getHeaders().getFirst("Authorization");
        if (t == null || t.isBlank()) return null;
        return t.startsWith("Bearer ") ? t.substring(7).trim() : t.trim();
    }

    public static class NoTokenException extends RuntimeException {
        public NoTokenException() { super("no token"); }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(Throwable cause) { super(cause); }
    }
}
