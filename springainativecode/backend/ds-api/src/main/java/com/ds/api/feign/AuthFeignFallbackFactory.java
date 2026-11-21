package com.ds.api.feign;

import com.ds.api.dto.AuthRequest;
import com.ds.api.dto.TokenResponse;
import com.ds.api.entity.User;
import com.ds.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Slf4j
@Component
public class AuthFeignFallbackFactory implements Function<Throwable, AuthFeignClient> {

    @Override
    public AuthFeignClient apply(Throwable throwable) {
        log.error("[auth-feign] fallback: {}", throwable.getMessage());
        return new AuthFeignClient() {
            @Override
            public Result<TokenResponse> login(AuthRequest req) {
                return Result.fail(503, "认证服务不可用");
            }

            @Override
            public Result<TokenResponse> register(AuthRequest req) {
                return Result.fail(503, "认证服务不可用");
            }

            @Override
            public Result<User> verify(String token) {
                return Result.fail(503, "认证服务不可用");
            }
        };
    }
}
