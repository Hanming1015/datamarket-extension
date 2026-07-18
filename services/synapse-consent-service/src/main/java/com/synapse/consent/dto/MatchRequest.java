package com.synapse.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Input to the field-level matching engine. Carried over from the monolith's
 * {@code DataAccessRequest} (trimmed to the fields the engine actually reads).
 * In Phase 3 this is populated by access-service before it calls consent-service.
 */
@Data
public class MatchRequest {

    @NotBlank(message = "datasetId must not be empty")
    private String datasetId;

    /** Consumer role/type, matched against a rule's allowedRoles. */
    @NotBlank(message = "consumerType must not be empty")
    private String consumerType;

    /** Intended purpose, matched against a rule's allowedPurposes. */
    @NotBlank(message = "purpose must not be empty")
    private String purpose;

    @NotEmpty(message = "requestedFields must not be empty")
    private List<String> requestedFields;
}
