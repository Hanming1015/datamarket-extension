package com.synapse.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付订单展示对象。 */
@Data
public class PaymentOrderVO {

    private String id;
    private String accessRequestId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
