package com.ds.auth.controller;

import com.ds.api.dto.AuthRequest;
import com.ds.api.dto.TokenResponse;
import com.ds.api.entity.User;
import com.ds.auth.service.AuthService;
import com.ds.common.exception.BusinessException;
import com.ds.common.model.Result;
import com.ds.common.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "登录 / 注册 / 校验 token")
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    @Operation(summary = "登录")
    public Result<TokenResponse> login(@Valid @RequestBody AuthRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/register")
    @Operation(summary = "注册")
    public Result<TokenResponse> register(@Valid @RequestBody AuthRequest req) {
        return Result.ok(authService.register(req));
    }

    @GetMapping("/verify")
    @Operation(summary = "校验 token，返回用户信息")
    public Result<User> verify(@RequestHeader(value = "Authorization", required = false) String token) {
        token = JwtUtil.fromHeader(token);
        if (token == null) throw BusinessException.unauthorized();
        return Result.ok(authService.verify(token));
    }

    @GetMapping("/me")
    @Operation(summary = "当前用户（网关透传 X-User-Id 头后，这里只是占位演示）")
    public Result<List<String>> me() {
        return Result.ok(List.of("ok"));
    }
}
