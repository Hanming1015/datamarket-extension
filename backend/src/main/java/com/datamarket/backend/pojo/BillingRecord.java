package com.datamarket.backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents the BillingRecord entity.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
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

    @com.baomidou.mybatisplus.annotation.TableField("sensitive_cost")
    private BigDecimal sensitiveFieldCost;

    private BigDecimal purposeMultiplier;

    private BigDecimal bulkDiscount;

    private BigDecimal cost;

    private LocalDate date;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime createdAt;
}
