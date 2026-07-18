package com.synapse.consent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.common.api.ResultCode;
import com.synapse.common.exception.BusinessException;
import com.synapse.consent.dto.CreateConsentRequest;
import com.synapse.consent.entity.ConsentRule;
import com.synapse.consent.mapper.ConsentRuleMapper;
import com.synapse.consent.service.ConsentRuleService;
import com.synapse.consent.vo.ConsentRuleVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Default {@link ConsentRuleService}. Migrated from the monolith's ConsentRuleServiceImpl,
 * dropping the SecurityContext + audit-log + cross-module DatasetMapper couplings
 * (audit becomes an MQ event in Phase 3; dataset lives in its own service now).
 */
@Service
public class ConsentRuleServiceImpl implements ConsentRuleService {

    @Autowired
    private ConsentRuleMapper consentRuleMapper;

    @Override
    public ConsentRuleVO create(CreateConsentRequest req, String ownerId) {
        ConsentRule rule = new ConsentRule();
        rule.setOwnerId(ownerId);
        rule.setDatasetId(req.getDatasetId());
        rule.setAllowedRoles(req.getAllowedRoles());
        rule.setAllowedPurposes(req.getAllowedPurposes());
        rule.setAllowedFields(req.getAllowedFields());
        rule.setDeniedFields(req.getDeniedFields() != null ? req.getDeniedFields() : List.of());
        rule.setValidFrom(LocalDate.now());
        rule.setValidUntil(req.getValidUntil());
        rule.setStatus("active");
        rule.setCreatedAt(LocalDateTime.now());
        consentRuleMapper.insert(rule);
        return toVO(rule);
    }

    @Override
    public void revoke(String id, String ownerId) {
        ConsentRule rule = consentRuleMapper.selectById(id);
        // Not-found and not-owned are indistinguishable to the caller (don't leak existence).
        if (rule == null || !ownerId.equals(rule.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        rule.setStatus("revoked");
        rule.setRevokedAt(LocalDateTime.now());
        consentRuleMapper.updateById(rule);
    }

    @Override
    public List<ConsentRuleVO> list(String ownerId, String datasetId, String status) {
        QueryWrapper<ConsentRule> qw = new QueryWrapper<>();
        qw.eq("owner_id", ownerId);
        qw.orderByDesc("created_at");
        if (StringUtils.hasText(datasetId)) {
            qw.eq("dataset_id", datasetId);
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status);
        }
        return consentRuleMapper.selectList(qw).stream().map(this::toVO).toList();
    }

    private ConsentRuleVO toVO(ConsentRule rule) {
        ConsentRuleVO vo = new ConsentRuleVO();
        BeanUtils.copyProperties(rule, vo);
        return vo;
    }
}
