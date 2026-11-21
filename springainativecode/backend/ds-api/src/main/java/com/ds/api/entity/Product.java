package com.ds.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_product")
@Schema(description = "商品")
public class Product implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "商品名")
    private String name;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "价格")
    private BigDecimal price;

    @Schema(description = "库存")
    private Integer stock;

    @Schema(description = "描述")
    private String description;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
