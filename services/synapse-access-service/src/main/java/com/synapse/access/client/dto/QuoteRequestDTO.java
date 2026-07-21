package com.synapse.access.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 客户端侧镜像:dataset-service 报价入参。
 */
@Data
public class QuoteRequestDTO {

    private List<String> allowedFields;
    private String purpose;
}
