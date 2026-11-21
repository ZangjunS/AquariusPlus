package com.ds.product.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring Boot 默认只提供 RedisTemplate<Object,Object> 与 StringRedisTemplate，
 * ProductService 需要 RedisTemplate<String,Object>，这里显式声明。
 *
 * 关键：必须复用 Spring Boot 的 ObjectMapper（它已注册 JavaTimeModule），
 * 否则 Product.createTime（LocalDateTime）序列化会抛
 * InvalidDefinitionException: Java 8 date/time type ... not supported by default，
 * 表现为「按 ID 查询一直降级 503」。
 * 另外 GenericJackson2JsonRedisSerializer 需要 default typing 才能在反序列化时还原具体类型。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory, ObjectMapper bootMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        StringRedisSerializer keySer = new StringRedisSerializer();

        ObjectMapper mapper = bootMapper.copy();
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer valueSer = new GenericJackson2JsonRedisSerializer(mapper);

        template.setKeySerializer(keySer);
        template.setHashKeySerializer(keySer);
        template.setValueSerializer(valueSer);
        template.setHashValueSerializer(valueSer);
        template.afterPropertiesSet();
        return template;
    }
}
