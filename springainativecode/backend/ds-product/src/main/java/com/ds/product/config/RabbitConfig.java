package com.ds.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ds.product.exchange";
    public static final String ROUTING_KEY = "product.changed";
    public static final String QUEUE = "ds.product.changed";
    public static final String BINDING = EXCHANGE + "." + ROUTING_KEY;

    /**
     * 统一用 JSON 序列化（Spring Boot 会自动把它装配到 RabbitTemplate 和监听容器工厂）。
     * 默认的 SimpleMessageConverter 走 Java 序列化，Spring AMQP 2.4+ 的反序列化白名单
     * 不放行 com.ds.product.mq.ProductEvent，消费端会 SecurityException 拒绝并无限重投，
     * 既淹掉日志又把服务拖到 500。改成 JSON 后生产/消费两侧都能直接读。
     */
    /**
     * 注意：必须传入 Spring Boot 的 ObjectMapper。new Jackson2JsonMessageConverter() 内部
     * 自己 new 一个裸 ObjectMapper，没注册 JavaTimeModule，Product 里的 LocalDateTime
     * 一序列化就抛 InvalidDefinitionException，表现为新增/修改/删除 500（事件发不出去）。
     */
    @Bean
    public MessageConverter jsonMessageConverter(org.springframework.beans.factory.ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapperProvider) {
        return new Jackson2JsonMessageConverter(mapperProvider.getObject());
    }

    @Bean
    public TopicExchange productExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue productQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding productBinding() {
        return BindingBuilder.bind(productQueue()).to(productExchange()).with(ROUTING_KEY);
    }
}
