package com.ds.product.controller;

import com.ds.api.entity.Product;
import com.ds.common.model.Result;
import com.ds.product.service.ProductService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "商品", description = "商品 CRUD + 缓存 + 消息发布")
public class ProductController {

    private final ProductService productService;

    @CircuitBreaker(name = "productRead", fallbackMethod = "fallbackPage")
    @Retry(name = "productRead")
    @GetMapping
    @Operation(summary = "分页查询")
    public Result<Page<Product>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size) {
        return Result.ok(productService.page(current, size));
    }

    @CircuitBreaker(name = "productRead", fallbackMethod = "fallbackById")
    @GetMapping("/{id}")
    @Operation(summary = "按 ID（走 Redis 缓存）")
    public Result<Product> byId(@PathVariable Long id) {
        return Result.ok(productService.getById(id));
    }

    @CircuitBreaker(name = "productRead", fallbackMethod = "fallbackLatest")
    @GetMapping("/latest")
    @Operation(summary = "最近 N 个")
    public Result<List<Product>> latest(@RequestParam(defaultValue = "5") int limit) {
        return Result.ok(productService.latest(limit));
    }

    @PostMapping
    @Operation(summary = "新增（发 MQ + 清缓存）")
    public Result<Product> create(@RequestBody Product p) {
        return Result.ok(productService.create(p));
    }

    @PutMapping
    @Operation(summary = "更新（发 MQ + 清缓存）")
    public Result<Product> update(@RequestBody Product p) {
        return Result.ok(productService.update(p));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除（发 MQ + 清缓存）")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }

    // ===== 熔断降级 =====
    @SuppressWarnings("unused")
    private Result<Page<Product>> fallbackPage(long current, long size, Throwable t) {
        log.warn("[circuit] page fallback: {}", t.getMessage());
        return Result.fail(503, "商品服务降级中");
    }

    @SuppressWarnings("unused")
    private Result<Product> fallbackById(Long id, Throwable t) {
        log.warn("[circuit] byId fallback: {}", t.getMessage());
        return Result.fail(503, "商品服务降级中");
    }

    @SuppressWarnings("unused")
    private Result<List<Product>> fallbackLatest(int limit, Throwable t) {
        log.warn("[circuit] latest fallback: {}", t.getMessage());
        return Result.fail(503, "商品服务降级中");
    }
}
