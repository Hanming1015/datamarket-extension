package com.synapse.dataset.entity;

import lombok.Data;

/**
 * 数据集单个字段的结构描述,是 {@code datasets.fields_schema}(JSON 数组)里的一项。
 * <ul>
 *   <li>{@code name} 列名</li>
 *   <li>{@code type} 推断类型(string/integer/decimal/boolean/date)</li>
 *   <li>{@code sensitive} 是否敏感字段(定价引擎据此加价;CSV 解析按关键字启发式初判,owner 可改)</li>
 * </ul>
 */
@Data
public class FieldSchema {

    private String name;
    private String type;
    private Boolean sensitive;

    public FieldSchema() {
    }

    public FieldSchema(String name, String type, Boolean sensitive) {
        this.name = name;
        this.type = type;
        this.sensitive = sensitive;
    }
}
