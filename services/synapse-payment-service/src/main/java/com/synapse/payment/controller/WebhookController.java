package com.synapse.payment.controller;

import com.synapse.common.api.Result;
import com.synapse.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付回调端点,前缀 {@code /webhooks/payment}。
 * <b>该前缀不在网关路由表内</b>——收银台(Stripe / mock)直接回调 payment-service,不经网关、不带 JWT。
 * c1 的 mock 版本不验签;接真 Stripe 时新增 {@code /webhooks/payment/stripe} 用 webhook secret 验签。
 */
@RestController
@RequestMapping("/webhooks/payment")
public class WebhookController {

    @Autowired
    private PaymentService paymentService;

    /** 模拟"支付成功"回调:标 PAID 并触发 payment.succeeded 扇出。 */
    @PostMapping("/mock")
    public Result<Void> mockPaid(@RequestParam String sessionId) {
        paymentService.handlePaidBySession(sessionId);
        return Result.ok();
    }
}
