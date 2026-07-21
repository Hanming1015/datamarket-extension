package com.synapse.access.client;

import com.synapse.access.client.dto.MatchRequestDTO;
import com.synapse.access.client.dto.MatchResultDTO;
import com.synapse.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * consent-service 的声明式客户端。经 Nacos 发现 + LoadBalancer 直连实例(不走网关)。
 * 内部调用无需 X-User-Id:匹配端点是纯引擎计算,不读身份头。
 */
@FeignClient(name = "synapse-consent-service", path = "/api/consent")
public interface ConsentClient {

    /** 字段级授权匹配,返回 approved/partial/rejected 决策。 */
    @PostMapping("/match")
    Result<MatchResultDTO> match(@RequestBody MatchRequestDTO req);
}
