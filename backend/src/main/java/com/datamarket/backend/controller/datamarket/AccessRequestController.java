package com.datamarket.backend.controller.datamarket;

import com.datamarket.backend.dto.DataAccessRequest;
import com.datamarket.backend.service.datamarket.AccessRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for managing AccessRequest related endpoints and operations.
 */

@RestController
@RequestMapping("/api/access")
public class AccessRequestController {

    @Autowired
    private AccessRequestService accessRequestService;

    @PostMapping("/request")
    public ResponseEntity<?> requestAccess(@RequestBody DataAccessRequest request) {
        //System.out.println("request: " + request);
        Map<String, Object> response = accessRequestService.processAccessRequest(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<?> getAccessRequests(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String datasetId) {
        return ResponseEntity.ok(accessRequestService.getAccessRequests(userId, datasetId));
    }
}