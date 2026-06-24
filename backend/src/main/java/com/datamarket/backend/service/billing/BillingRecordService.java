package com.datamarket.backend.service.billing;

import com.datamarket.backend.dto.DataAccessRequest;
import com.datamarket.backend.dto.PricingResult;
import com.datamarket.backend.pojo.AccessRequest;
import com.datamarket.backend.pojo.BillingRecord;

import java.util.List;

/**
 * Service interface for managing BillingRecord operations.
 */

public interface BillingRecordService {
    List<BillingRecord> getBillingSummary(String userId, String role);

    BillingRecord createBillForAccess(AccessRequest ar, PricingResult pricing, DataAccessRequest request);
}
