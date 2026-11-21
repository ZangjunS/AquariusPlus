package com.ds.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user")
@Schema(description = "用户")
public class User implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码（BCrypt）")
    private String password;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "角色 USER / ADMIN")
    private String role;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
