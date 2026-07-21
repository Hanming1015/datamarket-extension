package com.synapse.billing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.synapse.billing.entity.BillingRecord;
import com.synapse.billing.mapper.BillingRecordMapper;
import com.synapse.billing.service.BillingService;
import com.synapse.billing.vo.BillingRecordVO;
import com.synapse.billing.vo.PageResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BillingServiceImpl implements BillingService {

    @Autowired
    private BillingRecordMapper billingRecordMapper;

    @Override
    public PageResult<BillingRecordVO> listMine(String userId, long page, long size) {
        QueryWrapper<BillingRecord> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("created_at");
        IPage<BillingRecord> p = billingRecordMapper.selectPage(new Page<>(page, size), qw);
        List<BillingRecordVO> records = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private BillingRecordVO toVO(BillingRecord r) {
        BillingRecordVO vo = new BillingRecordVO();
        BeanUtils.copyProperties(r, vo);
        return vo;
    }
}
