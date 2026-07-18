package com.synapse.dataset.service;

import com.synapse.dataset.dto.PricingConfigRequest;
import com.synapse.dataset.dto.QuoteRequest;
import com.synapse.dataset.vo.PricingConfigVO;
import com.synapse.dataset.vo.PricingResult;

/**
 * 定价配置管理 + 报价计算。
 * 实现见 {@link com.synapse.dataset.service.impl.PricingServiceImpl}。
 */
public interface PricingService {

    /** 为自己的数据集设置/更新定价配置(按 datasetId upsert)。 */
    PricingConfigVO upsertConfig(String datasetId, PricingConfigRequest req, String ownerId);

    /** 查看某数据集的定价配置(市场透明,任意登录用户可看)。 */
    PricingConfigVO getConfig(String datasetId);

    /** 按授权字段 + 用途,对某数据集算一次访问账单。 */
    PricingResult quote(String datasetId, QuoteRequest req);
}
