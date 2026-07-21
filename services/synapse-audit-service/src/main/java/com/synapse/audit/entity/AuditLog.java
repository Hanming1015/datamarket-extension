package com.synapse.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志,对应 {@code synapse_audit.audit_logs}(迁移自单体 auditlog)。
 * 追加写、不可变;由 MQ 消费者从 AccessEvent 落库。
 */
@Data
@TableName("audit_logs")
public class AuditLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private LocalDateTime timestamp;

    private String userId;
    private String userName;

    /** 如 ACCESS_APPROVED / ACCESS_REJECTED。 */
    private String action;

    private String datasetId;
    private String datasetName;

    private String details;
}
