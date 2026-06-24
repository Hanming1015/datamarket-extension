package com.datamarket.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.datamarket.backend.pojo.Dataset;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper interface for data access operations related to Dataset.
 */

@Mapper
public interface DatasetMapper extends BaseMapper<Dataset> {
}
