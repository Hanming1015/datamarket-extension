package com.synapse.payment.provider;

import com.synapse.payment.entity.PaymentOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 模拟收银台(c1 默认)。不调任何外部服务:生成一个假 session,checkoutUrl 指向本地 mock webhook。
 * 消费者/测试对该 URL 发 POST 即模拟"支付成功"回调。接真 Stripe 后换成 StripePaymentProvider。
 */
@Component
@ConditionalOnProperty(name = "synapse.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public CheckoutSession createCheckout(PaymentOrder order) {
        String sessionId = "mock_sess_" + UUID.randomUUID().toString().replace("-", "");
        // 模拟托管收银台:真实场景是 Stripe 页面;这里给一个可读的本地占位 URL
        String checkoutUrl = "http://127.0.0.1:8085/webhooks/payment/mock?sessionId=" + sessionId;
        return new CheckoutSession(sessionId, checkoutUrl);
    }
}
