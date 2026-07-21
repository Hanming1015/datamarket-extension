package com.synapse.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 消费者提交访问申请入参。requesterId 不在此传——由网关注入的 X-User-Id 决定,防冒充。
 * consumerType 沿用单体语义:消费者自报角色/类型,由 owner 的授权规则(allowedRoles)裁决,
 * 自报不等于放行(报了规则里没有的类型即被拒),故不作为安全字段。
 */
@Data
public class CreateAccessRequest {

    @NotBlank(message = "datasetId must not be empty")
    private String datasetId;

    @NotBlank(message = "consumerType must not be empty")
    private String consumerType;

    @NotBlank(message = "purpose must not be empty")
    private String purpose;

    @NotEmpty(message = "requestedFields must not be empty")
    private List<String> requestedFields;
}
