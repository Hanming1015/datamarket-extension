package com.datamarket.backend.service.impl.consentmanagement;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.mapper.DatasetMapper;
import com.datamarket.backend.pojo.Dataset;
import com.datamarket.backend.pojo.User;
import com.datamarket.backend.utils.SecurityUtil;
import org.springframework.util.StringUtils;
import com.datamarket.backend.mapper.ConsentRuleMapper;
import com.datamarket.backend.pojo.ConsentRule;
import com.datamarket.backend.service.auditlog.AuditLogService;
import com.datamarket.backend.service.consentmanagement.ConsentRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the ConsentRuleService interface.
 */

@Service
public class ConsentRuleServiceImpl implements ConsentRuleService {

    @Autowired
    private ConsentRuleMapper consentRuleMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private DatasetMapper datasetMapper;


    @Override
    @Transactional
    public ConsentRule createConsentRule(Map<String, Object> body) {
        //System.out.println("Body: " + body);

        ConsentRule consentRule = new ConsentRule();

        User user = SecurityUtil.getCurrentUser();

        String datasetId = body.get("datasetId").toString();
        consentRule.setId(UUID.randomUUID().toString());
        consentRule.setDatasetId(datasetId);

        consentRule.setAllowedRoles((List<String>) body.get("allowedRoles"));
        consentRule.setAllowedPurposes((List<String>) body.get("allowedPurposes"));
        consentRule.setAllowedFields((List<String>) body.get("allowedFields"));

        Dataset dataset = datasetMapper.selectById(consentRule.getDatasetId());
        //System.out.println("Dataset: " + dataset);
        String datasetName = (dataset != null) ? dataset.getName() : "Unknown Dataset";

        if (body.containsKey("deniedFields")) {
            consentRule.setDeniedFields((List<String>) body.get("deniedFields"));
        } else {
            consentRule.setDeniedFields(List.of());
        }

        consentRule.setValidFrom(LocalDate.now());
        consentRule.setValidUntil(LocalDate.parse((String) body.get("validUntil")));
        consentRule.setStatus("active");
        consentRule.setCreatedAt(LocalDateTime.now());

        consentRuleMapper.insert(consentRule);

        //Silently record an audit log.
        auditLogService.addAuditLog(user.getId(), user.getUsername(), "consent_created", consentRule.getDatasetId(), datasetName,"Created consent for roles: " + String.join(",", consentRule.getAllowedRoles()));

        return consentRule;
    }

    @Override
    public void revokeConsentRule(String id) {
        ConsentRule consentRule = consentRuleMapper.selectById(id);
        if (consentRule == null) {
            throw new RuntimeException("No such consent rule！");
        }

        User user = SecurityUtil.getCurrentUser();

        Dataset dataset = datasetMapper.selectById(consentRule.getDatasetId());
        String datasetName = (dataset != null) ? dataset.getName() : "Unknown Dataset";

        consentRule.setStatus("revoked");

        consentRule.setRevokedAt(LocalDateTime.now());

        consentRuleMapper.updateById(consentRule);

        //Silently record an audit log.
        auditLogService.addAuditLog(user.getId(), user.getUsername(), "consent_revoked", consentRule.getDatasetId(), datasetName, "Revoked consent for roles: " + String.join(",", consentRule.getAllowedRoles()));
    }

    @Override
    public List<ConsentRule> getConsentRules(String datasetId, String status) {
        QueryWrapper<ConsentRule> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_at");

        if (StringUtils.hasText(datasetId)) {
            queryWrapper.eq("dataset_id", datasetId);
        }

        if (StringUtils.hasText(status)) {
            queryWrapper.eq("status", status);
        }

        return consentRuleMapper.selectList(queryWrapper);
    }

}
