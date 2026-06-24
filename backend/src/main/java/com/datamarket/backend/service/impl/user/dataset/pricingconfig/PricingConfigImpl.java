package com.datamarket.backend.service.impl.user.dataset.pricingconfig;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.mapper.DatasetMapper;
import com.datamarket.backend.mapper.PricingConfigMapper;
import com.datamarket.backend.pojo.PricingConfig;
import com.datamarket.backend.service.user.dataset.pricingconfig.PricingConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Configuration class for PricingImpl.
 */


@Service
public class PricingConfigImpl implements PricingConfigService {

    @Autowired
    private PricingConfigMapper pricingConfigMapper;

//    @Autowired
//    private DatasetMapper datasetMapper;

    @Override
    public PricingConfig addPricingConfig(PricingConfig pricingConfig) {
        QueryWrapper<PricingConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", pricingConfig.getDatasetId());
        PricingConfig existingConfig = pricingConfigMapper.selectOne(queryWrapper);

        if (existingConfig != null) {
            throw new RuntimeException("This dataset already has a price configuration. Please follow the update process.");
        }

        if (pricingConfig.getPerAccessBase() == null) pricingConfig.setPerAccessBase(BigDecimal.ZERO);
        if (pricingConfig.getPerField() == null) pricingConfig.setPerField(BigDecimal.ZERO);
        if (pricingConfig.getSensitiveFieldMultiplier() == null) pricingConfig.setSensitiveFieldMultiplier(BigDecimal.ONE);

        pricingConfig.setUpdatedAt(LocalDateTime.now());

        pricingConfigMapper.insert(pricingConfig);
        return pricingConfig;
    }

    @Override
    public PricingConfig getPricingConfig(String datasetId) {
        QueryWrapper<PricingConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", datasetId);

        return pricingConfigMapper.selectOne(queryWrapper);
    }

    @Override
    public PricingConfig updatePricingConfig(PricingConfig pricingConfig) {
        pricingConfigMapper.updateById(pricingConfig);
        return pricingConfig;
    }

    @Override
    public void removePricingConfig(String id) {
        pricingConfigMapper.deleteById(id);
    }
}
