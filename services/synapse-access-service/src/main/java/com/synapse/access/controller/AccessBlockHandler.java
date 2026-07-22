package com.synapse.access.controller;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.synapse.access.dto.CreateAccessRequest;
import com.synapse.access.vo.AccessRequestVO;
import com.synapse.common.api.Result;
import com.synapse.common.api.ResultCode;

/**
 * Sentinel blockHandler:被流控/熔断拦下时的兜底响应。
 * 方法签名必须与被保护方法一致 + 末尾追加 BlockException,且为 static(放在 blockHandlerClass 时)。
 * 返回 429 Result 而非默认 Sentinel 阻塞异常页,让前端拿到统一 envelope。
 */
public final class AccessBlockHandler {

    private AccessBlockHandler() {
    }

    public static Result<AccessRequestVO> createBlocked(
            String userId, CreateAccessRequest req, BlockException ex) {
        return Result.fail(ResultCode.TOO_MANY_REQUESTS.getCode(),
                "system busy, your request was throttled, please retry later");
    }
}
