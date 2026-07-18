package com.synapse.dataset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 新增/更新数据集定价配置入参(upsert,按 datasetId 唯一)。
 * 金额允许 0(免费),倍率须为正;折扣率范围在 service 内校验(0~1)。
 */
@Data
public class PricingConfigRequest {

    @NotNull(message = "perAccessBase must not be null")
    @PositiveOrZero(message = "perAccessBase must be >= 0")
    private BigDecimal perAccessBase;

    @NotNull(message = "perField must not be null")
    @PositiveOrZero(message = "perField must be >= 0")
    private BigDecimal perField;

    @NotNull(message = "sensitiveFieldMultiplier must not be null")
    @Positive(message = "sensitiveFieldMultiplier must be > 0")
    private BigDecimal sensitiveFieldMultiplier;

    /** 字段数阈值 -> 折扣率(0~1);可选。 */
    private Map<Integer, BigDecimal> bulkDiscountJson;

    /** 用途 -> 倍率(> 0);可选。 */
    private Map<String, BigDecimal> purposeMultiplierJson;
}
