package com.synapse.dataset.dto;

import com.synapse.dataset.entity.FieldSchema;
import lombok.Data;

import java.util.List;

/**
 * 更新数据集入参(仅 owner 可改自己的)。只允许改元数据与字段结构;
 * 数据集 id 走路径参数,ownerId / CSV 字段不可通过此接口修改。
 * 传了 {@code fieldsSchema} 时会同步重算 {@code fields}(列名列表)。
 */
@Data
public class UpdateDatasetRequest {

    private String name;

    private String description;

    private String category;

    /** owner 可借此覆盖敏感度标记等字段结构。 */
    private List<FieldSchema> fieldsSchema;
}
