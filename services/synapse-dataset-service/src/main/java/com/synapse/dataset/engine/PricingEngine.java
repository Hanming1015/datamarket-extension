package com.synapse.dataset.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.common.exception.BusinessException;
import com.synapse.dataset.entity.FieldSchema;
import com.synapse.dataset.entity.PricingConfig;
import com.synapse.dataset.mapper.PricingConfigMapper;
import com.synapse.dataset.vo.PricingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定价引擎(迁移自单体 PricingEngine,字段结构改为类型化 {@link FieldSchema})。
 * <p>公式:
 * <pre>
 *   字段费  = (普通字段费 + 敏感字段费) * (1 - 批量折扣)
 *   合计    = (基础访问费 + 字段费) * 用途倍率   → 2 位小数 HALF_UP
 * </pre>
 */
@Component
public class PricingEngine {

    @Autowired
    private PricingConfigMapper pricingConfigMapper;

    public PricingResult calculate(List<String> allowedFields, List<FieldSchema> fieldsSchema,
                                   String purpose, String datasetId) {
        // 1. 取该数据集的定价配置
        QueryWrapper<PricingConfig> qw = new QueryWrapper<>();
        qw.eq("dataset_id", datasetId);
        PricingConfig config = pricingConfigMapper.selectOne(qw);
        if (config == null) {
            throw new BusinessException("pricing config not found for dataset: " + datasetId);
        }

        // 2. 找出敏感字段名
        Set<String> sensitiveNames = new HashSet<>();
        if (fieldsSchema != null) {
            for (FieldSchema f : fieldsSchema) {
                if (Boolean.TRUE.equals(f.getSensitive())) {
                    sensitiveNames.add(f.getName());
                }
            }
        }

        // 3. 计数
        long normalCount = 0, sensitiveCount = 0;
        for (String field : allowedFields) {
            if (sensitiveNames.contains(field)) {
                sensitiveCount++;
            } else {
                normalCount++;
            }
        }

        // 4. 基础/字段费
        BigDecimal baseCost = config.getPerAccessBase() != null ? config.getPerAccessBase() : BigDecimal.ZERO;
        BigDecimal perField = config.getPerField() != null ? config.getPerField() : BigDecimal.ZERO;
        BigDecimal normalCost = perField.multiply(BigDecimal.valueOf(normalCount));
        BigDecimal sensitiveMultiplier = config.getSensitiveFieldMultiplier() != null
                ? config.getSensitiveFieldMultiplier() : BigDecimal.ONE;
        BigDecimal sensitiveCost = perField.multiply(sensitiveMultiplier).multiply(BigDecimal.valueOf(sensitiveCount));

        // 5. 用途倍率
        BigDecimal purposeMultiplier = BigDecimal.ONE;
        if (config.getPurposeMultiplierJson() != null && config.getPurposeMultiplierJson().containsKey(purpose)) {
            purposeMultiplier = new BigDecimal(config.getPurposeMultiplierJson().get(purpose).toString());
        }

        // 6. 批量折扣(按本次授权字段总数)
        BigDecimal bulkDiscount = calculateBulkDiscount(allowedFields.size(), config.getBulkDiscountJson());

        // 7. 合成
        BigDecimal discountFactor = BigDecimal.ONE.subtract(bulkDiscount);
        BigDecimal fieldsTotal = normalCost.add(sensitiveCost).multiply(discountFactor);
        BigDecimal total = baseCost.add(fieldsTotal).multiply(purposeMultiplier).setScale(2, RoundingMode.HALF_UP);

        return new PricingResult(baseCost, normalCost, sensitiveCost, purposeMultiplier, bulkDiscount, total);
    }

    /** 取满足 count>=阈值 的最高档折扣。 */
    private BigDecimal calculateBulkDiscount(int count, Map<Integer, BigDecimal> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal bestDiscount = BigDecimal.ZERO;
        int highestTier = 0;
        for (Map.Entry<Integer, BigDecimal> entry : tiers.entrySet()) {
            try {
                int tierKey = entry.getKey();
                BigDecimal discount = entry.getValue();
                if (count >= tierKey && tierKey > highestTier) {
                    highestTier = tierKey;
                    bestDiscount = discount;
                }
            } catch (Exception ignore) {
                // 跳过异常档位
            }
        }
        return bestDiscount;
    }
}
