package com.datamarket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datamarket.backend.pojo.BillingRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper interface for data access operations related to BillingRecord.
 */

@Mapper
public interface BillingRecordMapper extends BaseMapper<BillingRecord> {
}
