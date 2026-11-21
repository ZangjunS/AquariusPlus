package com.ds.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * CircuitBreaker 的 fallbackUri（forward:/fallback/product）需要真实处理器，否则熔断时直接 500。
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/product")
    public ResponseEntity<Map<String, Object>> product() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 503);
        body.put("message", "商品服务暂时不可用（熔断）");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
