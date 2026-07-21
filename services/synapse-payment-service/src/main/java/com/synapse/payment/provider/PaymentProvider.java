package com.synapse.payment.provider;

import com.synapse.payment.entity.PaymentOrder;

/**
 * 支付提供方抽象。c1 用 {@link MockPaymentProvider}(零外部依赖跑通全链路);
 * 接真 Stripe 时再加 StripePaymentProvider(创建真实 Checkout Session + webhook 验签),
 * 靠 {@code synapse.payment.provider} 配置切换,业务代码不变。
 */
public interface PaymentProvider {

    /** 为订单创建收银台会话。 */
    CheckoutSession createCheckout(PaymentOrder order);
}
