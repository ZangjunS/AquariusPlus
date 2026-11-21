package com.ds.product.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ds.product.exchange";
    public static final String ROUTING_KEY = "product.changed";
    public static final String QUEUE = "ds.product.changed";
    public static final String BINDING = EXCHANGE + "." + ROUTING_KEY;

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
