package com.synapse.consent.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Result of the field-level matching engine. Migrated as-is from the monolith.
 * {@code decision} is one of "approved" | "partial" | "rejected".
 */
@Data
public class MatchResult {

    private String decision;
    private List<String> allowedFields;
    private List<String> deniedFields;
    private Map<String, String> reasons;
    private LocalDate consentExpiresAt;
    private String denyReason;

    /** Fully approved: every requested field is allowed. */
    public static MatchResult permit(List<String> allowed, LocalDate expiry) {
        MatchResult r = new MatchResult();
        r.decision = "approved";
        r.allowedFields = allowed;
        r.deniedFields = List.of();
        r.reasons = Map.of();
        r.consentExpiresAt = expiry;
        return r;
    }

    /** Partially approved: some fields allowed, some denied (with reasons). */
    public static MatchResult partial(List<String> allowed, List<String> denied,
                                      Map<String, String> reasons, LocalDate expiry) {
        MatchResult r = new MatchResult();
        r.decision = "partial";
        r.allowedFields = allowed;
        r.deniedFields = denied;
        r.reasons = reasons;
        r.consentExpiresAt = expiry;
        return r;
    }

    /** Rejected outright (no valid rule / role / purpose / field). */
    public static MatchResult deny(String reason) {
        MatchResult r = new MatchResult();
        r.decision = "rejected";
        r.denyReason = reason;
        r.allowedFields = List.of();
        r.deniedFields = List.of();
        r.reasons = Map.of();
        return r;
    }
}
