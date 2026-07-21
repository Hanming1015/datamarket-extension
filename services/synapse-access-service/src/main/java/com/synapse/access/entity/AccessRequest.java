package com.synapse.access.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 访问申请实体,对应 {@code synapse_access.access_requests}。
 * 迁移自单体 {@code datamarket/AccessRequest},拆微服务后:
 * <ul>
 *   <li>{@code status} 存 {@link com.synapse.access.statemachine.AccessStatus} 的名字(字符串,DB 可读);</li>
 *   <li>{@code ownerId} 在建单时由 Feign 查数据集快照写入,供 owner 查"待我审批";</li>
 *   <li>字段列表 / 拒绝原因为 JSON 列,需 {@code autoResultMap=true} 让 {@link JacksonTypeHandler} 生效。</li>
 * </ul>
 */
@Data
@TableName(value = "access_requests", autoResultMap = true)
public class AccessRequest {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String datasetId;
    private String datasetName;

    private String requesterId;
    private String requesterName;

    private String consumerType;
    private String purpose;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> requestedFields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> allowedFields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> deniedFields;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> denialReasons;

    /** 状态机取值,见 {@link com.synapse.access.statemachine.AccessStatus}。 */
    private String status;

    private BigDecimal cost;

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;

    // ---- Phase 3 审批状态机 ----

    /** 建单时快照的数据集 owner(供 owner 查"待我审批")。 */
    private String ownerId;

    /** 实际点批准/驳回的人(3a 即数据集 owner)。 */
    private String approverId;

    private LocalDateTime approvedAt;
}
