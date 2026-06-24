package com.datamarket.backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents the ConsentRule entity.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "consent_rules", autoResultMap = true)
public class ConsentRule {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

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
