package com.synapse.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for {@link User}. BaseMapper gives CRUD for free.
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
