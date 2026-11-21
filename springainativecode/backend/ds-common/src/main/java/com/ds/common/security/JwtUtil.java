package com.ds.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具（JJWT 0.11，javax 命名空间）。
 * 密钥放 application.yml 的 jwt.secret；生产务必改 Nacos 下发。
 */
@Component
public class JwtUtil {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            ("ds-jwt-secret-key-please-change-me-0123456789").getBytes(StandardCharsets.UTF_8));
    private static final long TTL_MS = 24L * 60 * 60 * 1000; // 24h
    private static final SignatureAlgorithm ALG = SignatureAlgorithm.HS256;

    public String issue(Long userId, String username, String role) {
        Date now = new Date();
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("role", role == null ? "USER" : role);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + TTL_MS))
                .signWith(KEY, ALG)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .setSigningKey(KEY)
                .parseClaimsJws(token)
                .getBody();
    }

    public static String fromHeader(String token) {
        if (token == null || token.isBlank()) return null;
        return token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
    }
}
