package com.datamarket.backend.controller.auditlog;

import com.datamarket.backend.pojo.AuditLog;
import com.datamarket.backend.service.auditlog.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for managing AuditLog related endpoints and operations.
 */

@RestController
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping("/api/audit/logs")
    public ResponseEntity<?> getLog (@RequestParam(required = false) String action,
                                     @RequestParam(required = false) String userId,
                                     @RequestParam(required = false) String datasetId,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {

        List<AuditLog> result = auditLogService.getAuditLogs(action, userId, datasetId, from ,to);

        return ResponseEntity.ok(result);
    }
}
