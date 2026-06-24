package com.datamarket.backend.service.impl.auditlog;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.mapper.AuditLogMapper;
import com.datamarket.backend.pojo.AuditLog;
import com.datamarket.backend.service.auditlog.AuditLogService;
import com.datamarket.backend.service.impl.utils.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of the AuditLogService interface.
 */

@Service
public class AuditLogServiceImpl implements AuditLogService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Override
    public List<AuditLog> getAuditLogs(String action, String userId, String datasetId, String from, String to) {
        QueryWrapper<AuditLog> queryWrapper = new QueryWrapper<>();

        if (action != null && !action.isEmpty() && !"all".equals(action)) {
            queryWrapper.eq("action", action);
        }
        if (userId != null && !userId.isEmpty()) {
            queryWrapper.eq("user_id", userId);
        }
        if (datasetId != null && !datasetId.isEmpty()) {
            queryWrapper.eq("dataset_id", datasetId);
        }
        queryWrapper.orderByDesc("timestamp");

        return auditLogMapper.selectList(queryWrapper);
    }

    @Override
    public AuditLog addAuditLog(String userId, String username, String action, String datasetId, String datasetName, String details) {

        AuditLog auditLog = new AuditLog();

        auditLog.setId(UUID.randomUUID().toString());
        auditLog.setTimestamp(java.time.LocalDateTime.now());
        auditLog.setUserId(userId);
        auditLog.setUserName(username);
        auditLog.setAction(action);
        auditLog.setDatasetId(datasetId);
        auditLog.setDatasetName(datasetName);
        auditLog.setDetails(details);

        //System.out.println("AuditLog: " + auditLog);

        auditLogMapper.insert(auditLog);

        return auditLog;
    }


}
