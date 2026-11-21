package com.ds.product.mq;

import com.ds.product.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 本地回环消费者：证明 MQ 通道可用；生产里可独立成消费服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onEvent(ProductEvent e) {
        log.info("[mq-consumer] {} product id={} name={}",
                e.getType(), e.getProduct().getId(), e.getProduct().getName());
    }
}
