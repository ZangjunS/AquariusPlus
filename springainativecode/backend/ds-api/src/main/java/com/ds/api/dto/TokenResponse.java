package com.ds.api.dto;

import com.ds.api.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "JWT 令牌响应")
public class TokenResponse {
    @Schema(description = "JWT")
    private String token;
    @Schema(description = "用户信息（脱敏）")
    private User user;

    public static TokenResponse of(String token, User u) {
        TokenResponse r = new TokenResponse();
        r.token = token;
        r.user = u;
        return r;
    }
}
