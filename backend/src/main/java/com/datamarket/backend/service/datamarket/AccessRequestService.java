package com.datamarket.backend.service.datamarket;

import com.datamarket.backend.dto.DataAccessRequest;
import com.datamarket.backend.pojo.AccessRequest;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing AccessRequest operations.
 */

public interface AccessRequestService {
    Map<String, Object> processAccessRequest(DataAccessRequest request);
    
    List<AccessRequest> getAccessRequests(String userId, String datasetId);
}
