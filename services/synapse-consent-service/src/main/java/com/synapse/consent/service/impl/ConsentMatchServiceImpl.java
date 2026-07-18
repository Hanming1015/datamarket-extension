package com.synapse.consent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.synapse.consent.dto.MatchRequest;
import com.synapse.consent.entity.ConsentRule;
import com.synapse.consent.mapper.ConsentRuleMapper;
import com.synapse.consent.service.ConsentMatchService;
import com.synapse.consent.vo.MatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Field-level authorization matching engine (migrated from the monolith's ConsentMatchingEngine).
 * <p>
 * Pipeline: load active rules for the dataset → filter by validity window → role → purpose →
 * then a whitelist/blacklist field-collision pass (denied wins). Whitelist semantics:
 * a field not explicitly allowed is denied by default.
 */
@Service
public class ConsentMatchServiceImpl implements ConsentMatchService {

    @Autowired
    private ConsentRuleMapper consentRuleMapper;

    @Override
    public MatchResult match(MatchRequest request) {
        // 1. Active rules for this dataset
        QueryWrapper<ConsentRule> qw = new QueryWrapper<>();
        qw.eq("dataset_id", request.getDatasetId()).eq("status", "active");
        List<ConsentRule> activeRules = consentRuleMapper.selectList(qw);

        // 2. Within validity window
        LocalDate now = LocalDate.now();
        List<ConsentRule> validRules = activeRules.stream()
                .filter(r -> !now.isBefore(r.getValidFrom()) && !now.isAfter(r.getValidUntil()))
                .collect(Collectors.toList());
        if (validRules.isEmpty()) {
            return MatchResult.deny("No active and valid consent rules for this dataset");
        }

        // 3. Role check
        validRules = validRules.stream()
                .filter(r -> r.getAllowedRoles() != null && r.getAllowedRoles().contains(request.getConsumerType()))
                .collect(Collectors.toList());
        if (validRules.isEmpty()) {
            return MatchResult.deny("Consumer type not authorized");
        }

        // 4. Purpose check
        validRules = validRules.stream()
                .filter(r -> r.getAllowedPurposes() != null && r.getAllowedPurposes().contains(request.getPurpose()))
                .collect(Collectors.toList());
        if (validRules.isEmpty()) {
            return MatchResult.deny("Purpose not authorized");
        }

        // 5. Union of allowed / denied fields across remaining rules
        Set<String> allAllowed = validRules.stream()
                .filter(r -> r.getAllowedFields() != null)
                .flatMap(r -> r.getAllowedFields().stream())
                .collect(Collectors.toSet());
        Set<String> allDenied = validRules.stream()
                .filter(r -> r.getDeniedFields() != null)
                .flatMap(r -> r.getDeniedFields().stream())
                .collect(Collectors.toSet());

        // 6. Decide each requested field (denied wins; not-allowed defaults to denied)
        List<String> requested = request.getRequestedFields();
        List<String> finalAllowed = new ArrayList<>();
        List<String> finalDenied = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        for (String field : requested) {
            if (allDenied.contains(field)) {
                finalDenied.add(field);
                reasons.put(field, "Field explicitly denied by owner");
            } else if (allAllowed.contains(field)) {
                finalAllowed.add(field);
            } else {
                finalDenied.add(field);
                reasons.put(field, "Field not in consent allowed list");
            }
        }

        // 7. Earliest expiry among effective rules = authorization expiry
        LocalDate earliestExpiry = validRules.stream()
                .map(ConsentRule::getValidUntil)
                .min(LocalDate::compareTo)
                .orElse(now);

        // 8. Final decision
        if (finalAllowed.isEmpty()) {
            return MatchResult.deny("No requested fields are authorized");
        } else if (finalDenied.isEmpty()) {
            return MatchResult.permit(finalAllowed, earliestExpiry);
        } else {
            return MatchResult.partial(finalAllowed, finalDenied, reasons, earliestExpiry);
        }
    }
}
