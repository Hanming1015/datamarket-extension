package com.synapse.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.synapse.audit.entity.AuditLog;
import com.synapse.audit.mapper.AuditLogMapper;
import com.synapse.audit.service.AuditService;
import com.synapse.audit.vo.AuditLogVO;
import com.synapse.audit.vo.PageResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditServiceImpl implements AuditService {

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogVO> listMine(String userId, long page, long size) {
        QueryWrapper<AuditLog> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("timestamp");
        IPage<AuditLog> p = auditLogMapper.selectPage(new Page<>(page, size), qw);
        List<AuditLogVO> records = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private AuditLogVO toVO(AuditLog a) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(a, vo);
        return vo;
    }
}
