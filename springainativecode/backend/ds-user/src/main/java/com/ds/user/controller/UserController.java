package com.ds.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ds.api.entity.User;
import com.ds.user.service.UserService;
import com.ds.common.model.Result;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "用户", description = "用户查询示例")
public class UserController {

    private final UserService userService;

    @RateLimiter(name = "userRead")
    @GetMapping
    @Operation(summary = "分页查询")
    public Result<Page<User>> page(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size) {
        return Result.ok(userService.page(current, size));
    }

    @RateLimiter(name = "userRead")
    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查")
    public Result<User> byId(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @RateLimiter(name = "userRead")
    @GetMapping("/all")
    @Operation(summary = "全量（示例用，勿在生产开放）")
    public Result<List<User>> all() {
        return Result.ok(userService.listAll());
    }
}
