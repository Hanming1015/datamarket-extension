package com.synapse.billing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账单记录,对应 {@code synapse_billing.billing_records}(迁移自单体 billing)。
 * 3b 从 {@link com.synapse.common.event.AccessEvent} 只拿到总费用(cost),
 * 明细列(base/field/sensitive...)暂不回填;{@code sensitive_cost} 列名对应 POJO 的 sensitiveFieldCost。
 */
@Data
@TableName("billing_records")
public class BillingRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String userName;
    private String datasetId;
    private String datasetName;
    private String accessRequestId;

    private Integer queryCount;
    private Integer recordsAccessed;

    private BigDecimal baseCost;
    private BigDecimal fieldCost;

    @TableField("sensitive_cost")
    private BigDecimal sensitiveFieldCost;

    private BigDecimal purposeMultiplier;
    private BigDecimal bulkDiscount;

    private BigDecimal cost;

    private LocalDate date;
    private LocalDateTime createdAt;

    /** 与支付对账:3b 入账即 UNPAID,3c 支付成功后置 PAID。 */
    private String paymentStatus;
}
