package com.ds.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * CircuitBreaker 的 fallbackUri（forward:/fallback/product）需要真实处理器，否则熔断时直接 500。
 *
 * 注意：forward 会保留原始 HTTP 方法，所以这里必须收所有方法。
 * 只写 @GetMapping 时，熔断期间进来的 POST/PUT/DELETE 会被 Spring 判成 405 Method Not Allowed，
 * 表现为“写接口返回 405，但数据其实已经写进去了”，非常容易误判。
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/product")
    public ResponseEntity<Map<String, Object>> product() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", 503);
        body.put("message", "商品服务暂时不可用（熔断）");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
