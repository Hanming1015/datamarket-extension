package com.synapse.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据集定价配置,对应 {@code synapse_dataset.pricing_config}(每个数据集一条)。
 * 定价引擎据此计算账单(见 PricingEngine)。
 */
@Data
@TableName(value = "pricing_config", autoResultMap = true)
public class PricingConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String datasetId;

    /** 每次访问基础费。 */
    private BigDecimal perAccessBase;

    /** 每个字段单价。 */
    private BigDecimal perField;

    /** 敏感字段加价倍率。 */
    private BigDecimal sensitiveFieldMultiplier;

    /** 批量折扣:字段数阈值 -> 折扣率(如 {5:0.1} 表示 >=5 个字段打 9 折)。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Integer, BigDecimal> bulkDiscountJson;

    /** 用途倍率:purpose -> 倍率(如 {commercial:1.5})。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, BigDecimal> purposeMultiplierJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
