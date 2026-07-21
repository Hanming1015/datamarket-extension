package com.synapse.payment.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 建单结果:返回收银台 URL 供消费者去付款。 */
@Data
public class CheckoutVO {

    private String orderId;
    private String sessionId;
    private String checkoutUrl;
    private BigDecimal amount;
    private String currency;
    private String status;
}
