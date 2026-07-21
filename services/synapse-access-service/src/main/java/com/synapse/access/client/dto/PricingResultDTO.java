package com.synapse.access.client.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户端侧镜像:dataset-service 报价结果(一次访问的账单明细)。
 */
@Data
public class PricingResultDTO {

    private BigDecimal baseCost;
    private BigDecimal fieldCost;
    private BigDecimal sensitiveFieldCost;
    private BigDecimal purposeMultiplier;
    private BigDecimal bulkDiscount;
    private BigDecimal totalCost;
}
