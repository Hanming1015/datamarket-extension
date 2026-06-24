package com.datamarket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datamarket.backend.pojo.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper interface for data access operations related to User.
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
