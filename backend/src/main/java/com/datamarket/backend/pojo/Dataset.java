package com.datamarket.backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents the Dataset entity.
 */

@Data
@TableName(value = "datasets", autoResultMap = true)
public class Dataset {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> fieldsSchema;

    private String ownerId;

    private Integer recordCount;

    private String category;

    private LocalDateTime createdAt;

    @TableField(exist = false)
    private String ownerName;
}
