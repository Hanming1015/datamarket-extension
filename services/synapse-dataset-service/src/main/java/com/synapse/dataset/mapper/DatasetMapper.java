package com.synapse.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synapse.dataset.entity.Dataset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetMapper extends BaseMapper<Dataset> {
}
