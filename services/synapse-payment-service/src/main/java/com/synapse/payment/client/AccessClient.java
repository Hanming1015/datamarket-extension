package com.synapse.payment.client;

import com.synapse.payment.client.dto.AccessInternalDTO;
import com.synapse.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 调 access 的内部端点(不走网关,Feign 直连 lb://synapse-access-service)取金额/归属/状态。
 */
@FeignClient(name = "synapse-access-service", path = "/internal/access")
public interface AccessClient {

    @GetMapping("/{id}")
    Result<AccessInternalDTO> getInternal(@PathVariable("id") String id);
}
