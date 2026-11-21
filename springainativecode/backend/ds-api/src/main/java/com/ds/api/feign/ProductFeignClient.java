package com.ds.api.feign;

import com.ds.api.entity.Product;
import com.ds.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务 Feign 客户端
 */
@FeignClient(name = "ds-product", path = "/products", fallbackFactory = ProductFeignFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/{id}")
    Result<Product> getById(@PathVariable("id") Long id);

    @GetMapping("/latest")
    Result<java.util.List<Product>> listLatest(@org.springframework.web.bind.annotation.RequestParam("limit") int limit);
}
