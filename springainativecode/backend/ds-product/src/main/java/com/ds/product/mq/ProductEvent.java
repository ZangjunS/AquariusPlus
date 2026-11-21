package com.ds.product.mq;

import com.ds.api.entity.Product;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品变更事件：发布到 RabbitMQ 的 ds.product.exchange / product.changed
 * 消费者可以是任何服务（搜索、推荐、数据仓库等）
 */
@Data
public class ProductEvent implements Serializable {

    public enum Type { CREATED, UPDATED, DELETED }

    private Type type;
    private Product product;
    private long occurredAt;

    public static ProductEvent created(Product p) {
        ProductEvent e = new ProductEvent();
        e.setType(Type.CREATED);
        e.setProduct(p);
        e.setOccurredAt(System.currentTimeMillis());
        return e;
    }

    public static ProductEvent updated(Product p) {
        ProductEvent e = new ProductEvent();
        e.setType(Type.UPDATED);
        e.setProduct(p);
        e.setOccurredAt(System.currentTimeMillis());
        return e;
    }

    public static ProductEvent deleted(Product p) {
        ProductEvent e = new ProductEvent();
        e.setType(Type.DELETED);
        e.setProduct(p);
        e.setOccurredAt(System.currentTimeMillis());
        return e;
    }
}
