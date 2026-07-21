package com.synapse.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.synapse.common.api.Result;
import com.synapse.common.api.ResultCode;
import com.synapse.common.constant.MqConstants;
import com.synapse.common.event.PaymentEvent;
import com.synapse.common.exception.BusinessException;
import com.synapse.payment.client.AccessClient;
import com.synapse.payment.client.dto.AccessInternalDTO;
import com.synapse.payment.entity.PaymentOrder;
import com.synapse.payment.mapper.PaymentOrderMapper;
import com.synapse.payment.provider.CheckoutSession;
import com.synapse.payment.provider.PaymentProvider;
import com.synapse.payment.service.OutboxService;
import com.synapse.payment.service.PaymentService;
import com.synapse.payment.vo.CheckoutVO;
import com.synapse.payment.vo.PageResult;
import com.synapse.payment.vo.PaymentOrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String STATUS_UNPAID = "UNPAID";
    private static final String STATUS_PAID = "PAID";

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;

    @Autowired
    private AccessClient accessClient;

    @Autowired
    private PaymentProvider paymentProvider;

    @Autowired
    private OutboxService outboxService;

    @Override
    public CheckoutVO createPayment(String accessRequestId, String consumerId) {
        AccessInternalDTO access = unwrap(accessClient.getInternal(accessRequestId), "access lookup failed");
        // 归属校验:不是本人则当作不存在(不泄露)
        if (!consumerId.equals(access.getRequesterId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!"PENDING_PAYMENT".equals(access.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "request is not awaiting payment (status=" + access.getStatus() + ")");
        }

        // 幂等:idempotency_key = accessRequestId,一笔申请只建一个订单
        PaymentOrder existing = paymentOrderMapper.selectOne(new QueryWrapper<PaymentOrder>()
                .eq("idempotency_key", accessRequestId).last("limit 1"));
        if (existing != null) {
            if (STATUS_PAID.equals(existing.getStatus())) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "this request is already paid");
            }
            // 未支付:重新生成收银台会话(mock 无状态),复用同一订单
            CheckoutSession s = paymentProvider.createCheckout(existing);
            existing.setStripeSessionId(s.sessionId());
            existing.setUpdatedAt(LocalDateTime.now());
            paymentOrderMapper.updateById(existing);
            return toCheckout(existing, s.checkoutUrl());
        }

        PaymentOrder order = new PaymentOrder();
        order.setAccessRequestId(accessRequestId);
        order.setUserId(consumerId);
        order.setAmount(access.getCost());
        order.setCurrency("usd");
        order.setStatus(STATUS_UNPAID);
        order.setIdempotencyKey(accessRequestId);
        order.setCreatedAt(LocalDateTime.now());
        paymentOrderMapper.insert(order);

        CheckoutSession s = paymentProvider.createCheckout(order);
        order.setStripeSessionId(s.sessionId());
        order.setUpdatedAt(LocalDateTime.now());
        paymentOrderMapper.updateById(order);
        return toCheckout(order, s.checkoutUrl());
    }

    @Override
    @Transactional
    public void handlePaidBySession(String sessionId) {
        PaymentOrder order = paymentOrderMapper.selectOne(new QueryWrapper<PaymentOrder>()
                .eq("stripe_session_id", sessionId).last("limit 1"));
        if (order == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "unknown checkout session");
        }
        if (STATUS_PAID.equals(order.getStatus())) {
            return;   // 幂等:webhook 可能重复回调,已支付则不再改状态、不再发事件
        }
        order.setStatus(STATUS_PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setStripePaymentIntentId("mock_pi_" + UUID.randomUUID().toString().replace("-", ""));
        paymentOrderMapper.updateById(order);

        // 同事务写 outbox:标 PAID 与"发 payment.succeeded"最终一致
        outboxService.record(MqConstants.RK_PAYMENT_SUCCEEDED, toEvent(order));
    }

    @Override
    public PageResult<PaymentOrderVO> listMine(String userId, long page, long size) {
        QueryWrapper<PaymentOrder> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("created_at");
        IPage<PaymentOrder> p = paymentOrderMapper.selectPage(new Page<>(page, size), qw);
        List<PaymentOrderVO> records = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    // ---- helpers ----

    private PaymentEvent toEvent(PaymentOrder order) {
        PaymentEvent e = new PaymentEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType("PAYMENT_SUCCEEDED");
        e.setPaymentOrderId(order.getId());
        e.setAccessRequestId(order.getAccessRequestId());
        e.setBillingRecordId(order.getBillingRecordId());
        e.setUserId(order.getUserId());
        e.setAmount(order.getAmount());
        e.setCurrency(order.getCurrency());
        e.setStatus(STATUS_PAID);
        return e;
    }

    private CheckoutVO toCheckout(PaymentOrder order, String checkoutUrl) {
        CheckoutVO vo = new CheckoutVO();
        vo.setOrderId(order.getId());
        vo.setSessionId(order.getStripeSessionId());
        vo.setCheckoutUrl(checkoutUrl);
        vo.setAmount(order.getAmount());
        vo.setCurrency(order.getCurrency());
        vo.setStatus(order.getStatus());
        return vo;
    }

    private PaymentOrderVO toVO(PaymentOrder order) {
        PaymentOrderVO vo = new PaymentOrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }

    private <T> T unwrap(Result<T> result, String fallbackMsg) {
        if (result == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), fallbackMsg);
        }
        if (result.getCode() != ResultCode.SUCCESS.getCode() || result.getData() == null) {
            String msg = result.getMessage() != null ? result.getMessage() : fallbackMsg;
            throw new BusinessException(result.getCode(), msg);
        }
        return result.getData();
    }
}
