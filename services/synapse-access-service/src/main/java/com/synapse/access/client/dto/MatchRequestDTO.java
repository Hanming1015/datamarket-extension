package com.synapse.access.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 客户端侧镜像:consent-service 匹配入参(与其 MatchRequest 结构一致)。
 * 服务间不互相依赖 jar,故各留一份 DTO,保持解耦(3a 决策)。
 */
@Data
public class MatchRequestDTO {

    private String datasetId;
    private String consumerType;
    private String purpose;
    private List<String> requestedFields;
}
