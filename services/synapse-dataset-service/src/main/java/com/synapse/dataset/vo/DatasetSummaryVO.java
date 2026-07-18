package com.synapse.dataset.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据集列表用的轻量视图:不含 fields / fieldsSchema(列表页不需要整份字段结构,
 * 详情页再走 {@code GET /{id}} 拿完整 {@link DatasetVO})。
 */
@Data
public class DatasetSummaryVO {

    private String id;
    private String name;
    private String description;
    private String category;
    private String ownerId;
    private Integer recordCount;
    private String parseStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 跨服务字段,Phase 3 Feign 补,现为 null。 */
    private String ownerName;
}
