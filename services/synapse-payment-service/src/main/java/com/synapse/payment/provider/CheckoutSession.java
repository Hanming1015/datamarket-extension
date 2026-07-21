package com.synapse.payment.provider;

/**
 * 收银台会话结果:sessionId 供 webhook 回查订单,checkoutUrl 是消费者去付款的托管页面地址。
 */
public record CheckoutSession(String sessionId, String checkoutUrl) {
}
