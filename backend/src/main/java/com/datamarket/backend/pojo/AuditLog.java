package com.datamarket.backend.pojo;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents the AuditLog entity.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("audit_logs")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime timestamp;

    private String userId;

    private String userName;

    private String action;

    private String datasetId;

    private String datasetName;

    private String details;
}
