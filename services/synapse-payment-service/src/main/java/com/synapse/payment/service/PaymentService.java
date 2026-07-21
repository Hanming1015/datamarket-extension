package com.synapse.payment.service;

import com.synapse.payment.vo.CheckoutVO;
import com.synapse.payment.vo.PageResult;
import com.synapse.payment.vo.PaymentOrderVO;

public interface PaymentService {

    /** 消费者对 PENDING_PAYMENT 的申请建单并拿收银台 URL。 */
    CheckoutVO createPayment(String accessRequestId, String consumerId);

    /** 收银台回调(mock):按 sessionId 标 PAID + 同事务写 outbox。幂等。 */
    void handlePaidBySession(String sessionId);

    /** 我的支付订单,分页。 */
    PageResult<PaymentOrderVO> listMine(String userId, long page, long size);
}
