package com.synapse.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.audit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
