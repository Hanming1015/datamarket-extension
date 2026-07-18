package com.synapse.consent.service;

import com.synapse.consent.dto.CreateConsentRequest;
import com.synapse.consent.vo.ConsentRuleVO;

import java.util.List;

/**
 * Consent-rule management use-cases: create / revoke / list.
 * Implemented by {@link com.synapse.consent.service.impl.ConsentRuleServiceImpl}.
 */
public interface ConsentRuleService {

    /** Create a new active consent rule owned by {@code ownerId}. */
    ConsentRuleVO create(CreateConsentRequest req, String ownerId);

    /** Revoke a rule the caller owns (soft: status=revoked + revokedAt). */
    void revoke(String id, String ownerId);

    /** List the caller's own rules, optionally filtered by datasetId and/or status. */
    List<ConsentRuleVO> list(String ownerId, String datasetId, String status);
}
