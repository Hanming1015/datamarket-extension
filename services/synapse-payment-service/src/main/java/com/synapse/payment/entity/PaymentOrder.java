package com.synapse.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单,对应 {@code synapse_payment.payment_order}。
 * {@code idempotency_key} 唯一(建库有 uk),c1 用 accessRequestId 充当——保证一笔申请只建一个订单。
 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String accessRequestId;
    private String billingRecordId;
    private String userId;

    private BigDecimal amount;
    private String currency;

    /** UNPAID / PAID / EXPIRED / FAILED。 */
    private String status;

    private String stripeSessionId;
    private String stripePaymentIntentId;

    /** 防重复入账;c1 = accessRequestId。 */
    private String idempotencyKey;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
}
