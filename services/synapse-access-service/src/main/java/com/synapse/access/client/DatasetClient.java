package com.synapse.access.client;

import com.synapse.access.client.dto.DatasetDetailDTO;
import com.synapse.access.client.dto.PricingResultDTO;
import com.synapse.access.client.dto.QuoteRequestDTO;
import com.synapse.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * dataset-service 的声明式客户端。编排时用于:
 * ① 取数据集详情(datasetName + ownerId 快照,并校验存在);② 按授权字段报价。
 * 均为内部调用,不读身份头。
 */
@FeignClient(name = "synapse-dataset-service", path = "/api/datasets",
        fallback = DatasetClientFallback.class)
public interface DatasetClient {

    /** 数据集详情。 */
    @GetMapping("/{id}")
    Result<DatasetDetailDTO> getDataset(@PathVariable("id") String id);

    /** 按授权字段 + 用途报价。 */
    @PostMapping("/{datasetId}/quote")
    Result<PricingResultDTO> quote(@PathVariable("datasetId") String datasetId,
                                   @RequestBody QuoteRequestDTO req);
}
