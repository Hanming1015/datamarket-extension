package com.synapse.consent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Client-facing view of a consent rule. Mirrors the entity (no sensitive fields here),
 * kept separate so the DB entity is never returned directly.
 */
@Data
public class ConsentRuleVO {

    private String id;
    private String ownerId;
    private String datasetId;
    private List<String> allowedRoles;
    private List<String> allowedPurposes;
    private List<String> allowedFields;
    private List<String> deniedFields;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate validFrom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate validUntil;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime revokedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT")
    private LocalDateTime createdAt;
}
