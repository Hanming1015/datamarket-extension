package com.synapse.notification.service;

import com.synapse.notification.vo.NotificationVO;
import com.synapse.notification.vo.PageResult;

public interface NotificationService {

    /** 我的通知(按时间倒序,分页)。 */
    PageResult<NotificationVO> listMine(String userId, long page, long size);

    /** 标记已读(仅本人;非本人或不存在则视作未命中,幂等)。 */
    void markRead(String id, String userId);
}
