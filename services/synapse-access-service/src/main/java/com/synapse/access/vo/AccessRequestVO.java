package com.synapse.access.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 访问申请详情视图(单条)。含匹配决策与定价结果快照。
 */
@Data
public class AccessRequestVO {

    private String id;
    private String datasetId;
    private String datasetName;
    private String requesterId;
    private String requesterName;
    private String consumerType;
    private String purpose;

    private List<String> requestedFields;
    private List<String> allowedFields;
    private List<String> deniedFields;
    private Map<String, String> denialReasons;

    private String status;
    private BigDecimal cost;

    private String ownerId;
    private String approverId;

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime approvedAt;
}
