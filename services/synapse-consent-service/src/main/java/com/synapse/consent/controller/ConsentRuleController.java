package com.synapse.consent.controller;

import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import com.synapse.consent.dto.CreateConsentRequest;
import com.synapse.consent.service.ConsentRuleService;
import com.synapse.consent.vo.ConsentRuleVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consent-rule management endpoints under {@code /api/consent/rules}
 * (the gateway routes {@code /api/consent/**} here after verifying the JWT).
 */
@RestController
@RequestMapping("/api/consent/rules")
public class ConsentRuleController {

    @Autowired
    private ConsentRuleService consentRuleService;

    @PostMapping
    public Result<ConsentRuleVO> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @Valid @RequestBody CreateConsentRequest req) {
        return Result.ok(consentRuleService.create(req, userId));
    }

    @PutMapping("/{id}/revoke")
    public Result<Void> revoke(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id) {
        consentRuleService.revoke(id, userId);
        return Result.ok();
    }

    @GetMapping
    public Result<List<ConsentRuleVO>> list(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(required = false) String datasetId,
            @RequestParam(required = false) String status) {
        return Result.ok(consentRuleService.list(userId, datasetId, status));
    }
}
