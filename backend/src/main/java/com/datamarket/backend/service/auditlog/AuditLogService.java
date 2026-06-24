package com.datamarket.backend.service.auditlog;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datamarket.backend.pojo.AuditLog;
import java.util.List;

/**
 * Service interface for managing AuditLog operations.
 */

public interface AuditLogService {
    List<AuditLog> getAuditLogs(String action, String userId, String datasetId, String from, String to);

    AuditLog addAuditLog(String userId, String username,String action, String datasetId, String datasetName, String details);
}