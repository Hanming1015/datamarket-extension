package com.datamarket.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Data Transfer Object representing PricingResult.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingResult {
    private BigDecimal baseCost;

    private BigDecimal fieldCost;

    private BigDecimal sensitiveFieldCost;

    private BigDecimal purposeMultiplier;

    private BigDecimal bulkDiscount;

    private BigDecimal totalCost;
}
