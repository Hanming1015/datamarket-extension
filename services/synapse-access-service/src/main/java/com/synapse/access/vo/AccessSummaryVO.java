package com.synapse.access.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 访问申请列表视图(轻量:不带字段清单/拒绝原因)。用于"我的申请"/"待我审批"分页列表。
 */
@Data
public class AccessSummaryVO {

    private String id;
    private String datasetId;
    private String datasetName;
    private String requesterId;
    private String requesterName;
    private String purpose;
    private String status;
    private BigDecimal cost;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}
