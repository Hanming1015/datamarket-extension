package com.datamarket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datamarket.backend.pojo.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper interface for data access operations related to AuditLog.
 */

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

}
