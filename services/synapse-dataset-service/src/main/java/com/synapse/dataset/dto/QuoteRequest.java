package com.synapse.dataset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 报价入参:对某数据集、按给定的授权字段 + 用途,算一次访问账单。
 * Phase 3 由 access-service 在编排中复用同一定价引擎。
 */
@Data
public class QuoteRequest {

    @NotEmpty(message = "allowedFields must not be empty")
    private List<String> allowedFields;

    @NotBlank(message = "purpose must not be empty")
    private String purpose;
}
