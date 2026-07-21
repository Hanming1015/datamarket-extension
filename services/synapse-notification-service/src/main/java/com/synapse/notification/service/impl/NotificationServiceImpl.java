package com.synapse.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.synapse.notification.entity.Notification;
import com.synapse.notification.mapper.NotificationMapper;
import com.synapse.notification.service.NotificationService;
import com.synapse.notification.vo.NotificationVO;
import com.synapse.notification.vo.PageResult;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Override
    public PageResult<NotificationVO> listMine(String userId, long page, long size) {
        QueryWrapper<Notification> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("created_at");
        IPage<Notification> p = notificationMapper.selectPage(new Page<>(page, size), qw);
        List<NotificationVO> records = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public void markRead(String id, String userId) {
        // 只更新本人的这条;非本人 / 不存在则影响 0 行(不报错,天然幂等)
        notificationMapper.update(null, new UpdateWrapper<Notification>()
                .eq("id", id).eq("user_id", userId).set("is_read", true));
    }

    private NotificationVO toVO(Notification n) {
        NotificationVO vo = new NotificationVO();
        BeanUtils.copyProperties(n, vo);
        return vo;
    }
}
