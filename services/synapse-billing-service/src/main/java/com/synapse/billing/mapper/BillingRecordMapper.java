package com.synapse.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.billing.entity.BillingRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BillingRecordMapper extends BaseMapper<BillingRecord> {
}
