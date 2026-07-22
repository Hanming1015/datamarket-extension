package com.synapse.access.client;

import com.synapse.access.client.dto.DatasetDetailDTO;
import com.synapse.access.client.dto.PricingResultDTO;
import com.synapse.access.client.dto.QuoteRequestDTO;
import com.synapse.common.api.Result;
import com.synapse.common.api.ResultCode;
import org.springframework.stereotype.Component;

/**
 * dataset-service 熔断/异常时的降级出口(feign.sentinel.enabled 触发)。
 * getDataset / quote 均返回 503 Result,由编排层 unwrap() 翻成干净的业务失败,防雪崩。
 */
@Component
public class DatasetClientFallback implements DatasetClient {

    @Override
    public Result<DatasetDetailDTO> getDataset(String id) {
        return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(),
                "dataset service unavailable, please retry later");
    }

    @Override
    public Result<PricingResultDTO> quote(String datasetId, QuoteRequestDTO req) {
        return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(),
                "dataset service unavailable, please retry later");
    }
}
