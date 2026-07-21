package com.synapse.payment.client.dto;

import lombok.Data;

import java.math.BigDecimal;

/** access 内部视图的客户端镜像(服务间不共享 jar)。 */
@Data
public class AccessInternalDTO {

    private String id;
    private String requesterId;
    private String ownerId;
    private String datasetId;
    private String datasetName;
    private BigDecimal cost;
    private String status;
}
