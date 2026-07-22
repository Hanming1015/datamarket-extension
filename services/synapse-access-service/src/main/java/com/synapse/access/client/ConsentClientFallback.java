package com.synapse.access.client;

import com.synapse.access.client.dto.MatchRequestDTO;
import com.synapse.access.client.dto.MatchResultDTO;
import com.synapse.common.api.Result;
import com.synapse.common.api.ResultCode;
import org.springframework.stereotype.Component;

/**
 * consent-service 熔断/异常时的降级出口(feign.sentinel.enabled 触发)。
 * 返回 503 Result 而非抛异常或挂起,编排层 unwrap() 会把它翻成一次干净的业务失败,
 * 避免下游不可用把 access 也拖垮(雪崩)。
 */
@Component
public class ConsentClientFallback implements ConsentClient {

    @Override
    public Result<MatchResultDTO> match(MatchRequestDTO req) {
        return Result.fail(ResultCode.SERVICE_UNAVAILABLE.getCode(),
                "consent service unavailable, please retry later");
    }
}
