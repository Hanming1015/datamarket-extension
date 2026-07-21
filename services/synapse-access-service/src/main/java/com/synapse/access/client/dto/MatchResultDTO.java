package com.synapse.access.client.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 客户端侧镜像:consent-service 匹配结果。
 * decision ∈ "approved" | "partial" | "rejected"。
 */
@Data
public class MatchResultDTO {

    private String decision;
    private List<String> allowedFields;
    private List<String> deniedFields;
    private Map<String, String> reasons;
    private LocalDate consentExpiresAt;
    private String denyReason;
}
