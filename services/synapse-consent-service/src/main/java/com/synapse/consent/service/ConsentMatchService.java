package com.synapse.consent.service;

import com.synapse.consent.dto.MatchRequest;
import com.synapse.consent.vo.MatchResult;

/**
 * Field-level consent matching. Implemented by
 * {@link com.synapse.consent.service.impl.ConsentMatchServiceImpl}.
 */
public interface ConsentMatchService {

    /** Decide which requested fields are authorized: approved / partial / rejected. */
    MatchResult match(MatchRequest request);
}
