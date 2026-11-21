package com.ds.product.service;

import com.ds.api.entity.Product;
import com.ds.product.mq.ProductEvent;
import com.ds.product.repository.ProductMapper;
import com.ds.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY_PREFIX = "product:";

    public Page<Product> page(long current, long size) {
        return productMapper.selectPage(Page.of(current, size),
                new LambdaQueryWrapper<Product>().orderByDesc(Product::getId));
    }

    public Product getById(Long id) {
        String key = CACHE_KEY_PREFIX + id;
        Product cached = (Product) redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

        Product p = productMapper.selectById(id);
        if (p == null) throw BusinessException.notFound("商品不存在: " + id);
        redisTemplate.opsForValue().set(key, p, Duration.ofMinutes(10));
        return p;
    }

    public Product create(Product p) {
        productMapper.insert(p);
        publish(ProductEvent.created(p));
        evict(p.getId());
        return p;
    }

    public Product update(Product p) {
        Product old = productMapper.selectById(p.getId());
        if (old == null) throw BusinessException.notFound("商品不存在: " + p.getId());
        p.setUpdateTime(java.time.LocalDateTime.now());
        productMapper.updateById(p);
        publish(ProductEvent.updated(p));
        evict(p.getId());
        return p;
    }

    public void delete(Long id) {
        Product old = productMapper.selectById(id);
        productMapper.deleteById(id);
        if (old != null) publish(ProductEvent.deleted(old));
        evict(id);
    }

    public List<Product> latest(int limit) {
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getId).last("LIMIT " + Math.max(1, Math.min(limit, 100))));
    }

    private void evict(Long id) {
        redisTemplate.delete(CACHE_KEY_PREFIX + id);
    }

    private void publish(ProductEvent e) {
        rabbitTemplate.convertAndSend(
                com.ds.product.config.RabbitConfig.EXCHANGE,
                com.ds.product.config.RabbitConfig.ROUTING_KEY,
                e);
        log.info("[mq] publish {} product id={}", e.getType(), e.getProduct().getId());
    }
}
