package com.synapse.billing.service;

import com.synapse.billing.vo.BillingRecordVO;
import com.synapse.billing.vo.PageResult;

public interface BillingService {

    /** 我的账单(按下账时间倒序,分页)。 */
    PageResult<BillingRecordVO> listMine(String userId, long page, long size);
}
