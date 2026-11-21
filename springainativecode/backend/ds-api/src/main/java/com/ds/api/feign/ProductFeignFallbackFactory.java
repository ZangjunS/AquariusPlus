package com.ds.api.feign;

import com.ds.api.entity.Product;
import com.ds.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
public class ProductFeignFallbackFactory implements Function<Throwable, ProductFeignClient> {

    @Override
    public ProductFeignClient apply(Throwable throwable) {
        log.error("[product-feign] fallback: {}", throwable.getMessage());
        return new ProductFeignClient() {
            @Override
            public Result<Product> getById(Long id) {
                return Result.fail(503, "商品服务不可用");
            }

            @Override
            public Result<List<Product>> listLatest(int limit) {
                return Result.fail(503, "商品服务不可用");
            }
        };
    }
}
