package com.synapse.audit.controller;

import com.synapse.audit.service.AuditService;
import com.synapse.audit.vo.AuditLogVO;
import com.synapse.audit.vo.PageResult;
import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询端点,前缀 {@code /api/audit}(网关校验 JWT 后路由至此)。
 * 身份取网关注入的 X-User-Id;只看与自己相关的审计记录。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @GetMapping("/mine")
    public Result<PageResult<AuditLogVO>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(auditService.listMine(userId, page, size));
    }
}
