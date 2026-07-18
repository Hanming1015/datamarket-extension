package com.synapse.dataset.service;

import com.synapse.dataset.dto.CreateDatasetRequest;
import com.synapse.dataset.dto.UpdateDatasetRequest;
import com.synapse.dataset.vo.DatasetSummaryVO;
import com.synapse.dataset.vo.DatasetVO;
import com.synapse.dataset.vo.PageResult;
import com.synapse.dataset.vo.ParseStatusVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据集管理用例:创建 / 我的列表 / 市场列表 / 详情 / 更新 / 删除。
 * 实现见 {@link com.synapse.dataset.service.impl.DatasetServiceImpl}。
 */
public interface DatasetService {

    /** 创建数据集(owner = 网关注入的当前用户)。 */
    DatasetVO create(CreateDatasetRequest req, String ownerId);

    /** 当前用户自己的数据集(分页,含各状态,便于 owner 跟踪解析进度)。 */
    PageResult<DatasetSummaryVO> listOwn(String ownerId, long page, long size);

    /** 市场:仅列解析就绪(READY)的数据集(分页)。 */
    PageResult<DatasetSummaryVO> listAll(long page, long size);

    /** 数据集详情(任意登录用户可看)。 */
    DatasetVO get(String id);

    /** 解析状态(不走缓存,专供上传后轮询)。 */
    ParseStatusVO getParseStatus(String id);

    /** 更新自己的数据集元数据 / 字段结构。 */
    DatasetVO update(String id, UpdateDatasetRequest req, String ownerId);

    /** 删除自己的数据集。 */
    void remove(String id, String ownerId);

    /** 上传 CSV 到自己的数据集,存 MinIO 并触发异步解析(立即返回 UPLOADED)。 */
    DatasetVO uploadCsv(String datasetId, String ownerId, MultipartFile file);
}
