package com.synapse.consent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.consent.entity.ConsentRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@link ConsentRule}. BaseMapper gives CRUD for free.
 */
@Mapper
public interface ConsentRuleMapper extends BaseMapper<ConsentRule> {
}
