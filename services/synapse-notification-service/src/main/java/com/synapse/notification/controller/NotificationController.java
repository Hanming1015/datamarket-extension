package com.synapse.notification.controller;

import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import com.synapse.notification.service.NotificationService;
import com.synapse.notification.vo.NotificationVO;
import com.synapse.notification.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知端点,前缀 {@code /api/notifications}(网关校验 JWT 后路由至此)。身份取网关注入的 X-User-Id。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/mine")
    public Result<PageResult<NotificationVO>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(notificationService.listMine(userId, page, size));
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id) {
        notificationService.markRead(id, userId);
        return Result.ok();
    }
}
