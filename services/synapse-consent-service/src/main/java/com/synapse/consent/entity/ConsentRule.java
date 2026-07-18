package com.synapse.consent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Consent rule entity, backed by {@code synapse_consent.consent_rules}.
 * The four list columns are stored as JSON (MyBatis-Plus {@link JacksonTypeHandler});
 * {@code autoResultMap = true} is required for the type handler to apply on reads.
 */
@Data
@TableName(value = "consent_rules", autoResultMap = true)
public class ConsentRule {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** Creator = data owner who granted this consent (from the gateway's X-User-Id). */
    private String ownerId;

    private String datasetId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedRoles;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedPurposes;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedFields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> deniedFields;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate validFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate validUntil;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime revokedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime createdAt;
}
