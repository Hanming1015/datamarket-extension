package com.synapse.access.client.dto;

import lombok.Data;

/**
 * 客户端侧镜像:dataset-service 数据集详情(仅取编排需要的子集)。
 * Jackson 默认忽略未知字段,故不必镜像 DatasetVO 的全部列。
 */
@Data
public class DatasetDetailDTO {

    private String id;
    private String name;
    private String ownerId;
    /** NONE / UPLOADED / PARSING / READY / FAILED。 */
    private String parseStatus;
}
