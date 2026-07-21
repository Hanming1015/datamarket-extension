package com.synapse.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.payment.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {
}
