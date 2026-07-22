package com.synapse.access.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.synapse.access.dto.CreateAccessRequest;
import com.synapse.access.dto.RejectRequest;
import com.synapse.access.service.AccessService;
import com.synapse.access.vo.AccessRequestVO;
import com.synapse.access.vo.AccessSummaryVO;
import com.synapse.access.vo.PageResult;
import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 访问申请端点,前缀 {@code /api/access}(网关校验 JWT 后路由至此)。
 * 身份统一取网关注入的 X-User-Id;是消费者还是 owner 由业务按数据归属判定。
 */
@RestController
@RequestMapping("/api/access")
public class AccessController {

    @Autowired
    private AccessService accessService;

    /** 本实例监听端口,用于在多副本下肉眼观察负载均衡把请求打到了哪个实例(Phase 4b)。 */
    @Value("${server.port}")
    private String instancePort;

    /**
     * 探针:返回处理本请求的 access 实例端口。
     * 多副本时经网关反复调用,端口交替即证明 LoadBalancer 轮询生效。
     */
    @GetMapping("/whoami")
    public Result<Map<String, String>> whoami() {
        return Result.ok(Map.of("service", "synapse-access-service", "port", instancePort));
    }

    /**
     * 消费者提交访问申请(触发 Feign 编排:数据集快照 → 预筛 → 报价)。
     * Sentinel 资源 access:create 受 Nacos 下发的 QPS 流控保护;超阈值走 createBlocked 优雅降级。
     */
    @PostMapping
    @SentinelResource(value = "access:create",
            blockHandler = "createBlocked", blockHandlerClass = AccessBlockHandler.class)
    public Result<AccessRequestVO> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @Valid @RequestBody CreateAccessRequest req) {
        return Result.ok(accessService.create(req, userId));
    }

    /** 我提交的申请(分页)。 */
    @GetMapping("/mine")
    public Result<PageResult<AccessSummaryVO>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(accessService.listMine(userId, page, size));
    }

    /** 待我审批的申请(owner 视角,分页)。 */
    @GetMapping("/pending")
    public Result<PageResult<AccessSummaryVO>> pending(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(accessService.listPending(userId, page, size));
    }

    /** owner 批准。 */
    @PutMapping("/{id}/approve")
    public Result<AccessRequestVO> approve(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id) {
        return Result.ok(accessService.approve(id, userId));
    }

    /** owner 驳回(可带原因)。 */
    @PutMapping("/{id}/reject")
    public Result<AccessRequestVO> reject(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id,
            @RequestBody(required = false) RejectRequest req) {
        String reason = (req != null) ? req.getReason() : null;
        return Result.ok(accessService.reject(id, userId, reason));
    }

    /** 申请详情(仅 requester 或 owner 可见)。 */
    @GetMapping("/{id}")
    public Result<AccessRequestVO> get(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id) {
        return Result.ok(accessService.get(id, userId));
    }
}
