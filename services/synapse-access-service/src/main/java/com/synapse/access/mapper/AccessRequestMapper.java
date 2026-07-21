package com.synapse.access.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.access.entity.AccessRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问申请数据访问层。CRUD/分页由 MyBatis-Plus {@link BaseMapper} 提供。
 */
@Mapper
public interface AccessRequestMapper extends BaseMapper<AccessRequest> {
}
