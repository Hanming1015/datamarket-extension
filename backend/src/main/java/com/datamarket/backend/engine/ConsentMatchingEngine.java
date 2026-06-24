package com.datamarket.backend.engine;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.dto.DataAccessRequest;
import com.datamarket.backend.mapper.ConsentRuleMapper;
import com.datamarket.backend.pojo.ConsentRule;
import com.datamarket.backend.dto.MatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core engine handling business logic for ConsentMatching.
 */


@Component
public class ConsentMatchingEngine {

    @Autowired
    private ConsentRuleMapper consentRuleMapper;

    public MatchResult match(DataAccessRequest request) {

        // 1. Query all active consent rules for this dataset from the database
        QueryWrapper<ConsentRule> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_id", request.getDatasetId())
                .eq("status", "active");
        List<ConsentRule> activeRules = consentRuleMapper.selectList(queryWrapper);

        // 2. Filter: Check if the date is within the validity period
        LocalDate now = LocalDate.now();
        List<ConsentRule> validRules = activeRules.stream()
                .filter(r -> !now.isBefore(r.getValidFrom()) && !now.isAfter(r.getValidUntil()))
                .collect(Collectors.toList());

        if (validRules.isEmpty()) {
            return MatchResult.deny("No active and valid consent rules for this dataset");
        }

        // 3. Filter: Validate Role (consumerType)
        validRules = validRules.stream()
                .filter(r -> r.getAllowedRoles() != null && r.getAllowedRoles().contains(request.getConsumerType()))
                .collect(Collectors.toList());

        if (validRules.isEmpty()) {
            return MatchResult.deny("Consumer type not authorized");
        }

        // 4. Filter: Validate Purpose
        validRules = validRules.stream()
                .filter(r -> r.getAllowedPurposes() != null && r.getAllowedPurposes().contains(request.getPurpose()))
                .collect(Collectors.toList());

        if (validRules.isEmpty()) {
            return MatchResult.deny("Purpose not authorized");
        }

        // --- Entering Core: Field-level collision algorithm ---
        // 5. Extract the union of fields from remaining rules (denied fields take priority)
        Set<String> allAllowed = validRules.stream()
                .filter(r -> r.getAllowedFields() != null)
                .flatMap(r -> r.getAllowedFields().stream())
                .collect(Collectors.toSet());

        Set<String> allDenied = validRules.stream()
                .filter(r -> r.getDeniedFields() != null)
                .flatMap(r -> r.getDeniedFields().stream())
                .collect(Collectors.toSet());

        List<String> finalAllowed = new ArrayList<>();
        List<String> finalDenied = new ArrayList<>();
        Map<String, String> reasons = new LinkedHashMap<>();

        // 6. Determine each requested field one by one
        List<String> requested = request.getRequestedFields();
        if (requested == null || requested.isEmpty()) {
            return MatchResult.deny("No fields requested");
        }

        for (String field : requested) {
            if (allDenied.contains(field)) {
                // If hit black list, reject immediately
                finalDenied.add(field);
                reasons.put(field, "Field explicitly denied by owner");
            } else if (allAllowed.contains(field)) {
                // If in white list, allow
                finalAllowed.add(field);
            } else {
                // Neither in white nor black list, default reject (white list mechanism)
                finalDenied.add(field);
                reasons.put(field, "Field not in consent allowed list");
            }
        }

        // 7. Take the earliest expiry date among all effective rules as the authorization expiry date
        LocalDate earliestExpiry = validRules.stream()
                .map(ConsentRule::getValidUntil)
                .min(LocalDate::compareTo)
                .orElse(now);

        // 8. Determine final conclusion
        if (finalAllowed.isEmpty()) {
            return MatchResult.deny("No requested fields are authorized");
        } else if (finalDenied.isEmpty()) {
            return MatchResult.permit(finalAllowed, earliestExpiry);
        } else {
            return MatchResult.partial(finalAllowed, finalDenied, reasons, earliestExpiry);
        }
    }
}
