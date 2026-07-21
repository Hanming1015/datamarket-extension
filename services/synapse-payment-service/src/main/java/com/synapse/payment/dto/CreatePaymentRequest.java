package com.synapse.payment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 消费者对处于 PENDING_PAYMENT 的申请发起支付。userId 取自网关 X-User-Id,不在体内。 */
@Data
public class CreatePaymentRequest {

    @NotBlank(message = "accessRequestId must not be empty")
    private String accessRequestId;
}
