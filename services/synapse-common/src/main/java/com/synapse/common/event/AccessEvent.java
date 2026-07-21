package com.synapse.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 访问申请审批决定事件(Phase 3b)。access-service 在 owner 批准/驳回后发布一次,
 * 经 topic 交换机 {@code synapse.event.exchange} 按路由键
 * {@code access.approved} / {@code access.rejected} 扇出:
 * <ul>
 *   <li>billing-service 绑 {@code access.approved} —— 只有批准才入账;</li>
 *   <li>audit-service 绑 {@code access.#} —— 批准与驳回都记审计。</li>
 * </ul>
 * 承载两个消费者所需字段的并集,故一次发布多方消费(诚实的 fan-out),
 * 而非给每个消费者各发一条。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccessEvent extends BaseEvent {

    private String accessRequestId;

    /** 申请人(billing 的入账用户 / audit 的操作用户)。 */
    private String requesterId;
    private String requesterName;

    private String datasetId;
    private String datasetName;

    private String consumerType;
    private String purpose;

    /** 批准时的费用快照;驳回为 0。 */
    private BigDecimal cost;

    /** APPROVED / REJECTED —— audit 据此映射 action,routing key 据此决定。 */
    private String decision;

    /** 实际拍板人(owner)。 */
    private String approverId;
}
