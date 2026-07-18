package com.synapse.dataset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据集实体,对应 {@code synapse_dataset.datasets}。
 * {@code fields}(列名列表)和 {@code fieldsSchema}(字段结构)为 JSON 列,
 * 需 {@code autoResultMap = true} 让 {@link JacksonTypeHandler} 在读时生效。
 * <p>owner 由网关注入的 X-User-Id 写入(不再依赖单体 SecurityUtil)。
 */
@Data
@TableName(value = "datasets", autoResultMap = true)
public class Dataset {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    private String description;

    /** 列名列表,由 fieldsSchema 派生。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> fields;

    /** 字段结构(名/类型/敏感度);CSV 异步解析后自动回填。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<FieldSchema> fieldsSchema;

    private String ownerId;

    private Integer recordCount;

    private String category;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    // ---- Phase 2 CSV 上传相关 ----

    /** MinIO 对象键(桶内路径),上传后写入。 */
    private String fileObjectKey;

    private String fileName;

    private Long fileSize;

    /** 解析状态:NONE / UPLOADED / PARSING / READY / FAILED。 */
    private String parseStatus;
}
