package com.synapse.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.common.api.ResultCode;
import com.synapse.common.exception.BusinessException;
import com.synapse.dataset.dto.PricingConfigRequest;
import com.synapse.dataset.dto.QuoteRequest;
import com.synapse.dataset.engine.PricingEngine;
import com.synapse.dataset.entity.Dataset;
import com.synapse.dataset.entity.PricingConfig;
import com.synapse.dataset.mapper.DatasetMapper;
import com.synapse.dataset.mapper.PricingConfigMapper;
import com.synapse.dataset.service.PricingService;
import com.synapse.dataset.vo.PricingConfigVO;
import com.synapse.dataset.vo.PricingResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 默认 {@link PricingService}。定价配置仅数据集 owner 可改;报价对任意登录用户开放。
 */
@Service
public class PricingServiceImpl implements PricingService {

    @Autowired
    private PricingConfigMapper pricingConfigMapper;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private PricingEngine pricingEngine;

    @Override
    @CacheEvict(value = "pricing", key = "#datasetId")
    public PricingConfigVO upsertConfig(String datasetId, PricingConfigRequest req, String ownerId) {
        // 只有数据集 owner 能配定价
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || !ownerId.equals(dataset.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        validateRates(req);

        PricingConfig config = pricingConfigMapper.selectOne(new QueryWrapper<PricingConfig>().eq("dataset_id", datasetId));
        if (config == null) {
            config = new PricingConfig();
            config.setDatasetId(datasetId);
        }
        config.setPerAccessBase(req.getPerAccessBase());
        config.setPerField(req.getPerField());
        config.setSensitiveFieldMultiplier(req.getSensitiveFieldMultiplier());
        config.setBulkDiscountJson(req.getBulkDiscountJson());
        config.setPurposeMultiplierJson(req.getPurposeMultiplierJson());
        config.setUpdatedAt(LocalDateTime.now());

        if (config.getId() == null) {
            pricingConfigMapper.insert(config);
        } else {
            pricingConfigMapper.updateById(config);
        }
        return toVO(config);
    }

    @Override
    @Cacheable(value = "pricing", key = "#datasetId")
    public PricingConfigVO getConfig(String datasetId) {
        PricingConfig config = pricingConfigMapper.selectOne(
                new QueryWrapper<PricingConfig>().eq("dataset_id", datasetId));
        if (config == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toVO(config);
    }

    @Override
    public PricingResult quote(String datasetId, QuoteRequest req) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return pricingEngine.calculate(req.getAllowedFields(), dataset.getFieldsSchema(),
                req.getPurpose(), datasetId);
    }

    /** 折扣率须在 [0,1),用途倍率须为正。 */
    private void validateRates(PricingConfigRequest req) {
        if (req.getBulkDiscountJson() != null) {
            for (BigDecimal d : req.getBulkDiscountJson().values()) {
                if (d == null || d.signum() < 0 || d.compareTo(BigDecimal.ONE) >= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "bulk discount rate must be in [0, 1)");
                }
            }
        }
        if (req.getPurposeMultiplierJson() != null) {
            for (BigDecimal m : req.getPurposeMultiplierJson().values()) {
                if (m == null || m.signum() <= 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "purpose multiplier must be > 0");
                }
            }
        }
    }

    private PricingConfigVO toVO(PricingConfig config) {
        PricingConfigVO vo = new PricingConfigVO();
        BeanUtils.copyProperties(config, vo);
        return vo;
    }
}
