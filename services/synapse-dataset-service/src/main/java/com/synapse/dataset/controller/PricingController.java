package com.synapse.dataset.controller;

import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import com.synapse.dataset.dto.PricingConfigRequest;
import com.synapse.dataset.dto.QuoteRequest;
import com.synapse.dataset.service.PricingService;
import com.synapse.dataset.vo.PricingConfigVO;
import com.synapse.dataset.vo.PricingResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 定价端点,挂在数据集下:{@code /api/datasets/{id}/pricing|quote}。
 * pricing 配置仅 owner 可写、任意登录用户可读;quote 供潜在消费方试算。
 */
@RestController
@RequestMapping("/api/datasets/{datasetId}")
public class PricingController {

    @Autowired
    private PricingService pricingService;

    /** 设置/更新定价配置(owner)。 */
    @PostMapping("/pricing")
    public Result<PricingConfigVO> upsertPricing(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String datasetId,
            @Valid @RequestBody PricingConfigRequest req) {
        return Result.ok(pricingService.upsertConfig(datasetId, req, userId));
    }

    /** 查看定价配置。 */
    @GetMapping("/pricing")
    public Result<PricingConfigVO> getPricing(@PathVariable String datasetId) {
        return Result.ok(pricingService.getConfig(datasetId));
    }

    /** 按授权字段 + 用途报价。 */
    @PostMapping("/quote")
    public Result<PricingResult> quote(
            @PathVariable String datasetId,
            @Valid @RequestBody QuoteRequest req) {
        return Result.ok(pricingService.quote(datasetId, req));
    }
}
