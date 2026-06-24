package com.datamarket.backend.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.dto.PricingResult;
import com.datamarket.backend.mapper.DatasetMapper;
import com.datamarket.backend.mapper.PricingConfigMapper;
import com.datamarket.backend.pojo.PricingConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core engine handling business logic for Pricing calculation.
 * Responsibilities: Calculate the detailed bill for data access based on allowed fields and pricing config.
 */
@Component
public class PricingEngine {

    @Autowired
    private PricingConfigMapper pricingConfigMapper;

    @Autowired
    private DatasetMapper datasetMapper;

    public PricingResult calculate(List<String> allowedFields, List<Map<String, Object>> fieldsSchema, String purpose, String consumerId, String datasetId) {
        
        // 1. Query the latest pricing configuration from the database
        QueryWrapper<PricingConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", datasetId);
        PricingConfig config = pricingConfigMapper.selectOne(queryWrapper);

        if (config == null) {
            throw new RuntimeException("The system pricing configuration is missing, and thus billing cannot be performed.");
        }

        // 2. Identify sensitive fields
        Set<String> sensitiveNames = new HashSet<>();
        if (fieldsSchema != null) {
            for (Map<String, Object> fieldObj : fieldsSchema) {
                if (Boolean.TRUE.equals(fieldObj.get("sensitive"))) {
                    sensitiveNames.add((String) fieldObj.get("name"));
                }
            }
        }

        // 3. Count quantities
        long normalCount = 0;
        long sensitiveCount = 0;
        for (String field : allowedFields) {
            if (sensitiveNames.contains(field)) {
                sensitiveCount++;
            } else {
                normalCount++;
            }
        }

        // 4. Calculate base cost
        BigDecimal baseCost = config.getPerAccessBase() != null ? config.getPerAccessBase() : BigDecimal.ZERO;
        
        BigDecimal perField = config.getPerField() != null ? config.getPerField() : BigDecimal.ZERO;
        BigDecimal normalCost = perField.multiply(new BigDecimal(normalCount));
        
        BigDecimal sensitiveMultiplier = config.getSensitiveFieldMultiplier() != null ? config.getSensitiveFieldMultiplier() : BigDecimal.ONE;
        BigDecimal sensitiveCost = perField.multiply(sensitiveMultiplier).multiply(new BigDecimal(sensitiveCount));

        // 5. Get purpose multiplier
        BigDecimal purposeMultiplier = BigDecimal.ONE;
        if (config.getPurposeMultiplierJson() != null && config.getPurposeMultiplierJson().containsKey(purpose)) {
            Object rawMult = config.getPurposeMultiplierJson().get(purpose);
            purposeMultiplier = new BigDecimal(rawMult.toString());
        }

        // 6. Get bulk discount: using total count of authorized fields for this request
        int historyCount = allowedFields != null ? allowedFields.size() : 0; 
        BigDecimal bulkDiscount = calculateBulkDiscount(historyCount, config.getBulkDiscountJson());

        // 7. Core formula calculation
        // Field Cost = (Normal Field Cost + Sensitive Field Cost) * (1 - Bulk Discount)
        BigDecimal discountFactor = BigDecimal.ONE.subtract(bulkDiscount);
        BigDecimal fieldsTotal = normalCost.add(sensitiveCost).multiply(discountFactor);
        
        // Total Cost = (Base Access Fee + Discounted Field Cost) * Purpose Multiplier
        BigDecimal total = baseCost.add(fieldsTotal).multiply(purposeMultiplier);
        total = total.setScale(2, RoundingMode.HALF_UP);

        return new PricingResult(baseCost, normalCost, sensitiveCost, purposeMultiplier, bulkDiscount, total);
    }

    private BigDecimal calculateBulkDiscount(int count, Map<?, ?> tiers) {
        if (tiers == null || tiers.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal bestDiscount = BigDecimal.ZERO;
        int highestTier = 0;
        
        for (Map.Entry<?, ?> entry : tiers.entrySet()) {
            try {
                int tierKey = Integer.parseInt(entry.getKey().toString());
                BigDecimal discount = new BigDecimal(entry.getValue().toString());
                if (count >= tierKey && tierKey > highestTier) {
                    highestTier = tierKey;
                    bestDiscount = discount;
                }
            } catch (Exception e) {
                // Ignore parsing error nodes
            }
        }
        return bestDiscount;
    }
}
