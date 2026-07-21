package com.synapse.payment.controller;

import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import com.synapse.payment.dto.CreatePaymentRequest;
import com.synapse.payment.service.PaymentService;
import com.synapse.payment.vo.CheckoutVO;
import com.synapse.payment.vo.PageResult;
import com.synapse.payment.vo.PaymentOrderVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付端点,前缀 {@code /api/payments}(网关校验 JWT 后路由至此)。身份取网关注入的 X-User-Id。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /** 对 PENDING_PAYMENT 的申请发起支付,返回收银台 URL。 */
    @PostMapping
    public Result<CheckoutVO> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @Valid @RequestBody CreatePaymentRequest req) {
        return Result.ok(paymentService.createPayment(req.getAccessRequestId(), userId));
    }

    /** 我的支付订单。 */
    @GetMapping("/mine")
    public Result<PageResult<PaymentOrderVO>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(paymentService.listMine(userId, page, size));
    }
}
