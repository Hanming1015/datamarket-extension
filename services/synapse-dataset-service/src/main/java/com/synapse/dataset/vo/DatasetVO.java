package com.synapse.dataset.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.synapse.dataset.entity.FieldSchema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据集对外视图。{@code ownerName} 需跨服务查 auth-service,
 * 本 Phase 暂留空(Phase 3 用 Feign 补)。
 */
@Data
public class DatasetVO {

    private String id;
    private String name;
    private String description;
    private List<String> fields;
    private List<FieldSchema> fieldsSchema;
    private String ownerId;
    private Integer recordCount;
    private String category;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    private String fileName;
    private Long fileSize;
    private String parseStatus;

    /** 拥有者用户名;跨服务字段,Phase 3 Feign 补,现为 null。 */
    private String ownerName;
}
