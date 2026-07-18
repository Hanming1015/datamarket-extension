package com.synapse.dataset.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 定价配置对外视图。
 */
@Data
public class PricingConfigVO {

    private String id;
    private String datasetId;
    private BigDecimal perAccessBase;
    private BigDecimal perField;
    private BigDecimal sensitiveFieldMultiplier;
    private Map<Integer, BigDecimal> bulkDiscountJson;
    private Map<String, BigDecimal> purposeMultiplierJson;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
