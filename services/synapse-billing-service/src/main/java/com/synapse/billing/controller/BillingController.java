package com.synapse.billing.controller;

import com.synapse.billing.service.BillingService;
import com.synapse.billing.vo.BillingRecordVO;
import com.synapse.billing.vo.PageResult;
import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账单查询端点,前缀 {@code /api/billing}(网关校验 JWT 后路由至此)。
 * 身份取网关注入的 X-User-Id;账单按 user_id 收敛,只看自己的。
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired
    private BillingService billingService;

    @GetMapping("/mine")
    public Result<PageResult<BillingRecordVO>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(billingService.listMine(userId, page, size));
    }
}
