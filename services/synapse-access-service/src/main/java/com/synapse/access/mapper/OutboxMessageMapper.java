package com.synapse.access.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.access.entity.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {
}
