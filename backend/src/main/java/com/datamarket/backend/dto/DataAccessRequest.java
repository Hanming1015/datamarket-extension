package com.datamarket.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object representing DataAccessRequest.
 */

@Data
public class DataAccessRequest {
    private String requesterId;      // Requester ID (e.g., "req1")
    private String requesterName;    // Requester Name (e.g., "Stanford Medical")
    private String consumerType;     // Consumer type/role (e.g., "Research Institution")
    private String datasetId;        // Target dataset ID (e.g., "ds1")
    private String purpose;          // Request purpose (e.g., "Clinical Trials")
    private List<String> requestedFields; // List of requested fields for access
}
