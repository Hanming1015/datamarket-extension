package com.datamarket.backend.service.impl.datamarket;

import com.datamarket.backend.dto.DataAccessRequest;
import com.datamarket.backend.dto.PricingResult;
import com.datamarket.backend.engine.ConsentMatchingEngine;
import com.datamarket.backend.engine.PricingEngine;
import com.datamarket.backend.mapper.AccessRequestMapper;
import com.datamarket.backend.mapper.BillingRecordMapper;
import com.datamarket.backend.mapper.DatasetMapper;
import com.datamarket.backend.pojo.AccessRequest;
import com.datamarket.backend.pojo.BillingRecord;
import com.datamarket.backend.pojo.Dataset;
import com.datamarket.backend.pojo.User;
import com.datamarket.backend.service.auditlog.AuditLogService;
import com.datamarket.backend.service.billing.BillingRecordService;
import com.datamarket.backend.service.datamarket.AccessRequestService;
import com.datamarket.backend.dto.MatchResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datamarket.backend.utils.SecurityUtil;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Implementation of the AccessRequestService interface.
 */

@Service
public class AccessRequestServiceImpl implements AccessRequestService {

    @Autowired
    private ConsentMatchingEngine matchingEngine;

    @Autowired
    private PricingEngine pricingEngine;

    @Autowired
    private AccessRequestMapper accessRequestMapper;

    @Autowired private DatasetMapper datasetMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private BillingRecordService billingRecordService;

    @Override
    public List<AccessRequest> getAccessRequests(String userId, String datasetId) {
        QueryWrapper<AccessRequest> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("requested_at");

        if (StringUtils.hasText(userId)) {
            queryWrapper.eq("requester_id", userId);
        }

        if (StringUtils.hasText(datasetId)) {
            queryWrapper.eq("dataset_id", datasetId);
        }

        return accessRequestMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processAccessRequest(DataAccessRequest request) {
        System.out.println("Processing access request: " + request);

        Dataset dataset = datasetMapper.selectById(request.getDatasetId());
        String datasetName = (dataset != null) ? dataset.getName() : "Unknown Dataset";
        List<Map<String, Object>> fieldsSchema = (dataset != null) ? dataset.getFieldsSchema() : new ArrayList<>();

        // 1. Matching engine decision
        MatchResult match = matchingEngine.match(request);

        User user = SecurityUtil.getCurrentUser();

        // 2. Assemble POJO for persistence
        AccessRequest ar = new AccessRequest();
        ar.setId(UUID.randomUUID().toString());
        ar.setDatasetId(request.getDatasetId());
        ar.setDatasetName(datasetName);
        ar.setRequesterId(request.getRequesterId());
        ar.setRequesterName(request.getRequesterName());
        ar.setConsumerType(request.getConsumerType());
        ar.setPurpose(request.getPurpose());
        ar.setRequestedFields(request.getRequestedFields());
        ar.setRequestedAt(LocalDateTime.now());

        Map<String, Object> response = new LinkedHashMap<>();
        PricingResult pricing = null;

        // 3. Process branch based on decision
        if ("rejected".equals(match.getDecision())) {
            // Branch A: Completely rejected
            ar.setStatus("rejected");
            ar.setRespondedAt(LocalDateTime.now());
            ar.setCost(BigDecimal.ZERO);

            accessRequestMapper.insert(ar);

            auditLogService.addAuditLog(user.getId(), user.getUsername(), "request_rejected", request.getDatasetId(), datasetName,"Rejected: " + match.getDenyReason());
        } else {
            // Branch B: Approved or Partial approval
            ar.setStatus(match.getDecision()); // Will be set to "approved" or "partial"
            ar.setAllowedFields(match.getAllowedFields());
            ar.setDeniedFields(match.getDeniedFields());
            ar.setDenialReasons(match.getReasons());
            ar.setRespondedAt(LocalDateTime.now());

            // --- Core Step 2: Pricing engine calculation ---
            pricing = pricingEngine.calculate(match.getAllowedFields(), fieldsSchema, request.getPurpose(), request.getRequesterId(), request.getDatasetId());
            ar.setCost(pricing.getTotalCost());

            // Save access record
            accessRequestMapper.insert(ar);

            // Generate billing record
            billingRecordService.createBillForAccess(ar, pricing, request);

            // Save audit log
            auditLogService.addAuditLog(user.getId(), user.getUsername(), "data_accessed", request.getDatasetId(), datasetName,
                    "Fields allowed: " + match.getAllowedFields().size() + " | Cost: $" + pricing.getTotalCost());
        }

        // ==========================================
        // 4. Main Event: Manually assemble JSON response
        // ==========================================
        response.put("id", ar.getId());
        response.put("datasetId", ar.getDatasetId());
        response.put("datasetName", ar.getDatasetName());
        response.put("requesterId", ar.getRequesterId());
        response.put("requesterName", ar.getRequesterName());
        response.put("purpose", ar.getPurpose());
        response.put("requestedFields", ar.getRequestedFields());
        response.put("status", ar.getStatus());
        response.put("requestedAt", ar.getRequestedAt().toString());
        response.put("respondedAt", ar.getRespondedAt().toString());

        if (pricing != null) {
            // Add a dictionary for matching reasons in the response
            response.put("decision", Map.of(
                    "allowedFields", match.getAllowedFields(),
                    "deniedFields", match.getDeniedFields(),
                    "reasons", match.getReasons()
            ));
            // Add a dictionary for pricing details in the response
            response.put("pricing", Map.of(
                    "baseCost", pricing.getBaseCost(),
                    "fieldCost", pricing.getFieldCost(),
                    "sensitiveFieldCost", pricing.getSensitiveFieldCost(),
                    "purposeMultiplier", pricing.getPurposeMultiplier(),
                    "bulkDiscount", pricing.getBulkDiscount(),
                    "totalCost", pricing.getTotalCost()
            ));
        }

        return response;
    }
}