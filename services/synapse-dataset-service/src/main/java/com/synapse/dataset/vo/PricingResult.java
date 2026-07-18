package com.synapse.dataset.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 报价结果:一次访问的账单明细(迁移自单体 PricingResult)。
 */
@Data
public class PricingResult {

    /** 基础访问费。 */
    private BigDecimal baseCost;

    /** 普通字段费用(折扣前)。 */
    private BigDecimal fieldCost;

    /** 敏感字段费用(折扣前)。 */
    private BigDecimal sensitiveFieldCost;

    /** 用途倍率。 */
    private BigDecimal purposeMultiplier;

    /** 批量折扣率。 */
    private BigDecimal bulkDiscount;

    /** 最终合计(2 位小数,HALF_UP)。 */
    private BigDecimal totalCost;

    public PricingResult() {
    }

    public PricingResult(BigDecimal baseCost, BigDecimal fieldCost, BigDecimal sensitiveFieldCost,
                         BigDecimal purposeMultiplier, BigDecimal bulkDiscount, BigDecimal totalCost) {
        this.baseCost = baseCost;
        this.fieldCost = fieldCost;
        this.sensitiveFieldCost = sensitiveFieldCost;
        this.purposeMultiplier = purposeMultiplier;
        this.bulkDiscount = bulkDiscount;
        this.totalCost = totalCost;
    }
}
