package com.synapse.audit.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 审计日志展示对象。 */
@Data
public class AuditLogVO {

    private String id;
    private LocalDateTime timestamp;
    private String userId;
    private String userName;
    private String action;
    private String datasetId;
    private String datasetName;
    private String details;
}
