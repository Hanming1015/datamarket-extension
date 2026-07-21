package com.synapse.billing.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 账单展示对象。 */
@Data
public class BillingRecordVO {

    private String id;
    private String userId;
    private String datasetId;
    private String datasetName;
    private String accessRequestId;
    private BigDecimal cost;
    private LocalDate date;
    private LocalDateTime createdAt;
    private String paymentStatus;
}
