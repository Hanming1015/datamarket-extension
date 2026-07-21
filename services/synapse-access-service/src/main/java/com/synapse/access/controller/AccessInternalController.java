package com.synapse.access.controller;

import com.synapse.access.service.AccessService;
import com.synapse.access.vo.AccessInternalVO;
import com.synapse.common.api.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间内部端点,前缀 {@code /internal/access}。
 * <b>该前缀不在网关路由表内</b>,故不对外暴露,仅供其它服务经 Feign(lb://)直连。
 * payment-service 建单前用它取金额/归属/状态做校验。
 */
@RestController
@RequestMapping("/internal/access")
public class AccessInternalController {

    @Autowired
    private AccessService accessService;

    @GetMapping("/{id}")
    public Result<AccessInternalVO> get(@PathVariable String id) {
        return Result.ok(accessService.getInternal(id));
    }
}
