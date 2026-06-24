package com.datamarket.backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents the AccessRequest entity.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "access_requests", autoResultMap = true)
public class AccessRequest {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String datasetId;
    private String datasetName;
    private String requesterId;
    private String requesterName;
    private String consumerType;
    private String purpose;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> requestedFields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedFields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> deniedFields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> denialReasons;

    // status：'pending', 'approved', 'rejected', 'partial'
    private String status;

    private BigDecimal cost;

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
}