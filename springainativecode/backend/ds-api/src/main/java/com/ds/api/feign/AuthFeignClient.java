package com.ds.api.feign;

import com.ds.api.dto.AuthRequest;
import com.ds.api.dto.TokenResponse;
import com.ds.api.entity.User;
import com.ds.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 认证服务 Feign 客户端（给其它业务服务调用，比如校验 token）。
 */
@FeignClient(name = "ds-auth", path = "/auth", fallbackFactory = AuthFeignFallbackFactory.class)
public interface AuthFeignClient {

    @PostMapping("/login")
    Result<TokenResponse> login(@RequestBody AuthRequest req);

    @PostMapping("/register")
    Result<TokenResponse> register(@RequestBody AuthRequest req);

    @GetMapping("/verify")
    Result<User> verify(@RequestParam("token") String token);
}
