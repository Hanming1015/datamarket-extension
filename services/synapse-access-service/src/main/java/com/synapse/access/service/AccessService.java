package com.synapse.access.service;

import com.synapse.access.dto.CreateAccessRequest;
import com.synapse.access.vo.AccessInternalVO;
import com.synapse.access.vo.AccessRequestVO;
import com.synapse.access.vo.AccessSummaryVO;
import com.synapse.access.vo.PageResult;

/**
 * 访问申请编排 + 人工审批(Phase 3a)。
 * 编排链路:Feign 取数据集快照 → consent 预筛 → dataset 报价 → 落库 PENDING_APPROVAL。
 * 审批链路:数据集 owner approve → GRANTED / reject → REJECTED。
 */
public interface AccessService {

    /** 消费者提交申请。requesterId 来自网关注入的 X-User-Id。 */
    AccessRequestVO create(CreateAccessRequest req, String requesterId);

    /** 我(消费者)提交的申请,分页。 */
    PageResult<AccessSummaryVO> listMine(String requesterId, long page, long size);

    /** 待我(owner)审批的申请(PENDING_APPROVAL),分页。 */
    PageResult<AccessSummaryVO> listPending(String ownerId, long page, long size);

    /** owner 批准 → PENDING_PAYMENT(3c:待支付)。 */
    AccessRequestVO approve(String id, String ownerId);

    /** owner 驳回 → REJECTED。 */
    AccessRequestVO reject(String id, String ownerId, String reason);

    /** 详情(仅 requester 或 owner 可见,否则当作不存在)。 */
    AccessRequestVO get(String id, String currentUserId);

    /** 支付成功回调驱动:PENDING_PAYMENT → GRANTED(幂等,供 MQ 消费者调用)。 */
    void markGrantedByPayment(String accessRequestId);

    /** 服务间内部视图(无身份收敛,仅经不公开的 /internal 路径 + Feign 直连访问)。 */
    AccessInternalVO getInternal(String id);
}
