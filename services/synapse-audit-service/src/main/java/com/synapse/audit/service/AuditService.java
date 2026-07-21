package com.synapse.audit.service;

import com.synapse.audit.vo.AuditLogVO;
import com.synapse.audit.vo.PageResult;

public interface AuditService {

    /** 我的审计记录(按时间倒序,分页)。 */
    PageResult<AuditLogVO> listMine(String userId, long page, long size);
}
