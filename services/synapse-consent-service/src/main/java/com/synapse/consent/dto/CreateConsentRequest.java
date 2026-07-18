package com.synapse.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload to create a consent rule. Replaces the monolith's untyped {@code Map<String,Object>} body.
 * Server sets id / validFrom / status / createdAt — not accepted from the client.
 */
@Data
public class CreateConsentRequest {

    @NotBlank(message = "datasetId must not be empty")
    private String datasetId;

    @NotNull(message = "allowedRoles must not be null")
    private List<String> allowedRoles;

    @NotNull(message = "allowedPurposes must not be null")
    private List<String> allowedPurposes;

    @NotNull(message = "allowedFields must not be null")
    private List<String> allowedFields;

    /** Optional blacklist; defaults to empty when omitted. */
    private List<String> deniedFields;

    @NotNull(message = "validUntil must not be null")
    private LocalDate validUntil;
}
