package com.synapse.dataset.dto;

import com.synapse.dataset.entity.FieldSchema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建数据集入参。id / ownerId / createdAt / CSV 相关字段均由服务端设置。
 * {@code fieldsSchema} 可选:允许先手工声明字段;若随后上传 CSV,异步解析会覆盖回填。
 */
@Data
public class CreateDatasetRequest {

    @NotBlank(message = "name must not be empty")
    private String name;

    private String description;

    private String category;

    /** 可选的手工字段结构;不传则等 CSV 解析回填。 */
    private List<FieldSchema> fieldsSchema;
}
