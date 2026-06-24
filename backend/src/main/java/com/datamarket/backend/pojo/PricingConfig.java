package com.datamarket.backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration class for Pricing.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "pricing_config", autoResultMap = true)
public class PricingConfig {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String datasetId;
    
    private BigDecimal perAccessBase;

    private BigDecimal perField;

    private BigDecimal sensitiveFieldMultiplier;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Integer, BigDecimal> bulkDiscountJson;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, BigDecimal> purposeMultiplierJson;

    private LocalDateTime updatedAt;
}
