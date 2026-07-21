package com.synapse.access.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.synapse.access.client.ConsentClient;
import com.synapse.access.client.DatasetClient;
import com.synapse.access.client.dto.DatasetDetailDTO;
import com.synapse.access.client.dto.MatchRequestDTO;
import com.synapse.access.client.dto.MatchResultDTO;
import com.synapse.access.client.dto.PricingResultDTO;
import com.synapse.access.client.dto.QuoteRequestDTO;
import com.synapse.access.dto.CreateAccessRequest;
import com.synapse.access.entity.AccessRequest;
import com.synapse.access.mapper.AccessRequestMapper;
import com.synapse.access.service.AccessService;
import com.synapse.access.service.OutboxService;
import com.synapse.access.statemachine.AccessStatus;
import com.synapse.access.vo.AccessInternalVO;
import com.synapse.access.vo.AccessRequestVO;
import com.synapse.access.vo.AccessSummaryVO;
import com.synapse.access.vo.PageResult;
import com.synapse.common.api.Result;
import com.synapse.common.api.ResultCode;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.AccessEvent;
import com.synapse.common.exception.BusinessException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 默认 {@link AccessService}。编排 = Feign 组合 + 状态机;人工审批 = owner 归属校验 + 合法流转。
 */
@Service
public class AccessServiceImpl implements AccessService {

    private static final String LOCK_PREFIX = "access:lock:";
    /** 驳回原因写入 denialReasons 的保留键(表无独立列,复用 JSON 列承载)。 */
    private static final String OWNER_REASON_KEY = "(owner)";

    @Autowired
    private AccessRequestMapper accessRequestMapper;

    @Autowired
    private ConsentClient consentClient;

    @Autowired
    private DatasetClient datasetClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private OutboxService outboxService;

    @Value("${synapse.access.lock-ttl-seconds:10}")
    private long lockTtlSeconds;

    @Override
    public AccessRequestVO create(CreateAccessRequest req, String requesterId) {
        // ① Redis 幂等锁:同一 requester+dataset 的并发/重复提交先被挡住(3a 首次把 Redis 当锁用)
        String lockKey = LOCK_PREFIX + requesterId + ":" + req.getDatasetId();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(lockTtlSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "a request for this dataset is being processed, please do not resubmit");
        }
        try {
            // ②a 持久幂等:同一 requester 对同一 dataset 已有待审申请 → 不重复受理
            //     (Redis 锁只防并发双击,这一步防"已提交过还没被处理"的重复)
            Long pending = accessRequestMapper.selectCount(new QueryWrapper<AccessRequest>()
                    .eq("requester_id", requesterId)
                    .eq("dataset_id", req.getDatasetId())
                    .eq("status", AccessStatus.PENDING_APPROVAL.name()));
            if (pending != null && pending > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "you already have a pending request for this dataset");
            }

            // ②b Feign→ dataset:取 owner + name 快照,并校验数据集存在
            DatasetDetailDTO dataset = unwrap(datasetClient.getDataset(req.getDatasetId()),
                    "dataset lookup failed");
            if (requesterId.equals(dataset.getOwnerId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "cannot request access to your own dataset");
            }

            // ③ Feign→ consent:字段级预筛
            MatchRequestDTO matchReq = new MatchRequestDTO();
            matchReq.setDatasetId(req.getDatasetId());
            matchReq.setConsumerType(req.getConsumerType());
            matchReq.setPurpose(req.getPurpose());
            matchReq.setRequestedFields(req.getRequestedFields());
            MatchResultDTO match = unwrap(consentClient.match(matchReq), "consent match failed");

            // ④ 组装实体(快照)
            AccessRequest ar = new AccessRequest();
            ar.setDatasetId(req.getDatasetId());
            ar.setDatasetName(dataset.getName());
            ar.setRequesterId(requesterId);
            ar.setConsumerType(req.getConsumerType());
            ar.setPurpose(req.getPurpose());
            ar.setRequestedFields(req.getRequestedFields());
            ar.setOwnerId(dataset.getOwnerId());
            ar.setRequestedAt(LocalDateTime.now());

            if ("rejected".equals(match.getDecision())) {
                // 分支 A:引擎直拒 → 终态 REJECTED,不定价、不进审批
                ar.setStatus(AccessStatus.REJECTED.name());
                ar.setAllowedFields(List.of());
                ar.setDeniedFields(match.getDeniedFields());
                ar.setDenialReasons(withReason(match.getReasons(), match.getDenyReason()));
                ar.setCost(BigDecimal.ZERO);
                ar.setRespondedAt(LocalDateTime.now());
                accessRequestMapper.insert(ar);
                return toVO(ar);
            }

            // 分支 B:approved/partial → 报价 → 落 PENDING_APPROVAL,等 owner 拍板
            ar.setAllowedFields(match.getAllowedFields());
            ar.setDeniedFields(match.getDeniedFields());
            ar.setDenialReasons(match.getReasons());

            QuoteRequestDTO quoteReq = new QuoteRequestDTO();
            quoteReq.setAllowedFields(match.getAllowedFields());
            quoteReq.setPurpose(req.getPurpose());
            PricingResultDTO pricing = unwrap(
                    datasetClient.quote(req.getDatasetId(), quoteReq), "quote failed");
            ar.setCost(pricing.getTotalCost());

            ar.setStatus(AccessStatus.PENDING_APPROVAL.name());
            accessRequestMapper.insert(ar);
            return toVO(ar);
        } finally {
            // 提交流程结束即释放锁——它只防"处理中重复提交",不是长期占用
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public PageResult<AccessSummaryVO> listMine(String requesterId, long page, long size) {
        QueryWrapper<AccessRequest> qw = new QueryWrapper<>();
        qw.eq("requester_id", requesterId).orderByDesc("requested_at");
        return toSummaryPage(accessRequestMapper.selectPage(new Page<>(page, size), qw));
    }

    @Override
    public PageResult<AccessSummaryVO> listPending(String ownerId, long page, long size) {
        QueryWrapper<AccessRequest> qw = new QueryWrapper<>();
        qw.eq("owner_id", ownerId)
                .eq("status", AccessStatus.PENDING_APPROVAL.name())
                .orderByDesc("requested_at");
        return toSummaryPage(accessRequestMapper.selectPage(new Page<>(page, size), qw));
    }

    @Override
    @Transactional
    public AccessRequestVO approve(String id, String ownerId) {
        AccessRequest ar = mustOwn(id, ownerId);
        // 3c:批准不再直接授权,先进"待支付";付款成功后由 payment.succeeded 推进到 GRANTED
        transition(ar, AccessStatus.PENDING_PAYMENT);
        ar.setApproverId(ownerId);
        ar.setApprovedAt(LocalDateTime.now());
        ar.setRespondedAt(LocalDateTime.now());
        accessRequestMapper.updateById(ar);
        // outbox:与状态更新同事务落一条待发消息(补上 3b 的 dual-write 缺口)
        // billing 收 access.approved 先记 UNPAID 账单,等支付成功再对账为 PAID
        outboxService.record(MqConstants.RK_ACCESS_APPROVED, toEvent(ar, "APPROVED"));
        return toVO(ar);
    }

    @Override
    @Transactional
    public AccessRequestVO reject(String id, String ownerId, String reason) {
        AccessRequest ar = mustOwn(id, ownerId);
        transition(ar, AccessStatus.REJECTED);
        ar.setApproverId(ownerId);
        ar.setApprovedAt(LocalDateTime.now());
        ar.setRespondedAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            ar.setDenialReasons(withReason(ar.getDenialReasons(), reason));
        }
        accessRequestMapper.updateById(ar);
        // 驳回只进审计(billing 不绑 access.rejected)
        outboxService.record(MqConstants.RK_ACCESS_REJECTED, toEvent(ar, "REJECTED"));
        return toVO(ar);
    }

    @Override
    @Transactional
    public void markGrantedByPayment(String accessRequestId) {
        AccessRequest ar = accessRequestMapper.selectById(accessRequestId);
        if (ar == null) {
            return;   // 找不到就丢弃(不该发生;不抛以免无谓重投)
        }
        AccessStatus current = AccessStatus.fromDb(ar.getStatus());
        if (current == AccessStatus.GRANTED) {
            return;   // 幂等:payment.succeeded 可能重复投递,已授权则跳过
        }
        transition(ar, AccessStatus.GRANTED);
        ar.setRespondedAt(LocalDateTime.now());
        accessRequestMapper.updateById(ar);
    }

    @Override
    public AccessRequestVO get(String id, String currentUserId) {
        AccessRequest ar = accessRequestMapper.selectById(id);
        if (ar == null
                || (!currentUserId.equals(ar.getRequesterId()) && !currentUserId.equals(ar.getOwnerId()))) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toVO(ar);
    }

    @Override
    public AccessInternalVO getInternal(String id) {
        AccessRequest ar = accessRequestMapper.selectById(id);
        if (ar == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        AccessInternalVO vo = new AccessInternalVO();
        BeanUtils.copyProperties(ar, vo);
        return vo;
    }

    // ---- helpers ----

    /** 加载并校验 owner 归属;不存在或非本人一律当作不存在(不泄露)。 */
    private AccessRequest mustOwn(String id, String ownerId) {
        AccessRequest ar = accessRequestMapper.selectById(id);
        if (ar == null || !ownerId.equals(ar.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return ar;
    }

    /** 状态机流转校验:非法边一律 400,把"当前什么状态"回给调用方。 */
    private void transition(AccessRequest ar, AccessStatus target) {
        AccessStatus current = AccessStatus.fromDb(ar.getStatus());
        if (current == null || !current.canTransitionTo(target)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "illegal state transition: " + ar.getStatus() + " -> " + target.name());
        }
        ar.setStatus(target.name());
    }

    /** 把一条补充原因并入 reasons(不改原 map,拷一份写)。 */
    private Map<String, String> withReason(Map<String, String> base, String reason) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (reason != null && !reason.isBlank()) {
            merged.put(OWNER_REASON_KEY, reason);
        }
        return merged;
    }

    /** 解包下游 Result:非 200 或空 data 视为下游业务失败,透传其 message。 */
    private <T> T unwrap(Result<T> result, String fallbackMsg) {
        if (result == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), fallbackMsg);
        }
        if (result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            String msg = result.getMessage() != null ? result.getMessage() : fallbackMsg;
            throw new BusinessException(result.getCode(), msg);
        }
        return result.getData();
    }

    /** 由申请行组装扇出事件;eventId 供消费端幂等去重。 */
    private AccessEvent toEvent(AccessRequest ar, String decision) {
        AccessEvent e = new AccessEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType("ACCESS_" + decision);
        e.setAccessRequestId(ar.getId());
        e.setRequesterId(ar.getRequesterId());
        e.setRequesterName(ar.getRequesterName());
        e.setDatasetId(ar.getDatasetId());
        e.setDatasetName(ar.getDatasetName());
        e.setConsumerType(ar.getConsumerType());
        e.setPurpose(ar.getPurpose());
        e.setCost("APPROVED".equals(decision) ? ar.getCost() : BigDecimal.ZERO);
        e.setDecision(decision);
        e.setApproverId(ar.getApproverId());
        return e;
    }

    private AccessRequestVO toVO(AccessRequest ar) {
        AccessRequestVO vo = new AccessRequestVO();
        BeanUtils.copyProperties(ar, vo);
        return vo;
    }

    private PageResult<AccessSummaryVO> toSummaryPage(IPage<AccessRequest> p) {
        List<AccessSummaryVO> records = p.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private AccessSummaryVO toSummary(AccessRequest ar) {
        AccessSummaryVO vo = new AccessSummaryVO();
        BeanUtils.copyProperties(ar, vo);
        return vo;
    }
}
