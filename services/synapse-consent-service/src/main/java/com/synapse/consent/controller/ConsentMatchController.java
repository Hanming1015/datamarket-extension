package com.synapse.consent.controller;

import com.synapse.common.api.Result;
import com.synapse.consent.dto.MatchRequest;
import com.synapse.consent.service.ConsentMatchService;
import com.synapse.consent.vo.MatchResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Field-level matching endpoint. Called by access-service (Phase 3) during
 * access-request orchestration to decide which fields are authorized.
 */
@RestController
@RequestMapping("/api/consent/match")
public class ConsentMatchController {

    @Autowired
    private ConsentMatchService consentMatchService;

    @PostMapping
    public Result<MatchResult> match(@Valid @RequestBody MatchRequest req) {
        return Result.ok(consentMatchService.match(req));
    }
}
