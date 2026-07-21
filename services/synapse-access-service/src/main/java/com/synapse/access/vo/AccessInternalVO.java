package com.synapse.access.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务间内部视图:payment-service 建单前 Feign 取此校验金额/归属/状态。
 * 经 {@code /internal/access/{id}} 暴露——该前缀不在网关路由表内,故仅服务间可达,不对外公开。
 */
@Data
public class AccessInternalVO {

    private String id;
    private String requesterId;
    private String ownerId;
    private String datasetId;
    private String datasetName;
    private BigDecimal cost;
    private String status;
}
