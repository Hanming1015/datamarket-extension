package com.synapse.access.statemachine;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * 访问申请状态机(替代单体的 "pending"/"approved" 魔法字符串)。
 *
 * <p>3a 用到的路径:
 * <pre>
 *   (建单·引擎直拒) ────────────► REJECTED
 *   (建单·预筛通过) → PENDING_APPROVAL ──owner批准──► GRANTED
 *                                    └──owner驳回──► REJECTED
 * </pre>
 * {@link #PENDING_PAYMENT} / {@link #CANCELLED} 及其流转是 3c 支付闸门的预留,
 * 3a 不产生这两个状态,但先在状态机里声明好合法边,3c 无需再改本枚举。
 */
public enum AccessStatus {

    PENDING_APPROVAL,
    PENDING_PAYMENT,   // 3c:owner 批准后进入待支付
    GRANTED,           // 终态:授权生效
    REJECTED,          // 终态:引擎直拒或 owner 驳回
    CANCELLED;         // 3c 终态:支付超时/取消

    private static final EnumMap<AccessStatus, Set<AccessStatus>> TRANSITIONS =
            new EnumMap<>(AccessStatus.class);

    static {
        TRANSITIONS.put(PENDING_APPROVAL, EnumSet.of(GRANTED, REJECTED, PENDING_PAYMENT));
        TRANSITIONS.put(PENDING_PAYMENT, EnumSet.of(GRANTED, CANCELLED));
        TRANSITIONS.put(GRANTED, EnumSet.noneOf(AccessStatus.class));
        TRANSITIONS.put(REJECTED, EnumSet.noneOf(AccessStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(AccessStatus.class));
    }

    /** 从当前状态能否合法流转到 target。 */
    public boolean canTransitionTo(AccessStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 宽松解析 DB 里的字符串(未知值 → null,交由调用方处理)。 */
    public static AccessStatus fromDb(String value) {
        if (value == null) {
            return null;
        }
        try {
            return AccessStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
