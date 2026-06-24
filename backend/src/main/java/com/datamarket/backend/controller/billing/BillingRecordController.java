package com.datamarket.backend.controller.billing;

import com.datamarket.backend.pojo.BillingRecord;
import com.datamarket.backend.service.billing.BillingRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for managing BillingRecord related endpoints and operations.
 */

@RestController
public class BillingRecordController {

    @Autowired
    private BillingRecordService billingRecordService;

    @GetMapping("/api/billing/summary")
    public ResponseEntity<?> getBillingSummary(@RequestParam String userId, @RequestParam String role) {
        List<BillingRecord> records = billingRecordService.getBillingSummary(userId, role);

        int totalQueries = records.stream().mapToInt(r -> r.getQueryCount() == null ? 0 : r.getQueryCount()).sum();
        int totalRecords = records.stream().mapToInt(r -> r.getRecordsAccessed() == null ? 0 : r.getRecordsAccessed()).sum();
        double totalCost = records.stream().mapToDouble(r -> r.getCost() == null ? 0.0 : r.getCost().doubleValue()).sum();

        List<Map<String, Object>> recordList = records.stream().map(r -> Map.<String, Object>of(
                "id", r.getId(), 
                "userId", r.getUserId(), 
                "userName", r.getUserName(),
                "datasetId", r.getDatasetId(), 
                "datasetName", r.getDatasetName(),
                "queryCount", r.getQueryCount() == null ? 0 : r.getQueryCount(), 
                "recordsAccessed", r.getRecordsAccessed() == null ? 0 : r.getRecordsAccessed(),
                "cost", r.getCost() == null ? 0.0 : r.getCost(), 
                "date", r.getDate() != null ? r.getDate().toString() : ""
        )).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "stats", Map.of(
                "totalQueries", totalQueries,
                "totalRecordsAccessed", totalRecords,
                "totalCost", Math.round(totalCost * 100.0) / 100.0,
                "totalRevenue", "owner".equals(role) ? Math.round(totalCost * 100.0) / 100.0 : 0
            ),
            "records", recordList
        ));
    }
}
