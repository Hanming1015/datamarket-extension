package com.synapse.access.dto;

import lombok.Data;

/**
 * owner 驳回申请入参。原因可选,记入响应/审计。
 */
@Data
public class RejectRequest {

    /** 驳回原因(可选)。 */
    private String reason;
}
