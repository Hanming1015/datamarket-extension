package com.synapse.dataset.controller;

import com.synapse.common.api.Result;
import com.synapse.common.constant.SecurityConstants;
import com.synapse.dataset.dto.CreateDatasetRequest;
import com.synapse.dataset.dto.UpdateDatasetRequest;
import com.synapse.dataset.service.DatasetService;
import com.synapse.dataset.vo.DatasetSummaryVO;
import com.synapse.dataset.vo.DatasetVO;
import com.synapse.dataset.vo.PageResult;
import com.synapse.dataset.vo.ParseStatusVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据集管理端点,前缀 {@code /api/datasets}(网关校验 JWT 后路由至此)。
 */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    @Autowired
    private DatasetService datasetService;

    /** 我的数据集(分页,含各状态)。 */
    @GetMapping("/list")
    public Result<PageResult<DatasetSummaryVO>> listOwn(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(datasetService.listOwn(userId, page, size));
    }

    /** 市场:仅解析就绪的数据集(分页)。 */
    @GetMapping("/all")
    public Result<PageResult<DatasetSummaryVO>> listAll(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(datasetService.listAll(page, size));
    }

    /** 数据集详情。 */
    @GetMapping("/{id}")
    public Result<DatasetVO> get(@PathVariable String id) {
        return Result.ok(datasetService.get(id));
    }

    /** 解析状态(不走缓存,供上传后轮询)。 */
    @GetMapping("/{id}/parse-status")
    public Result<ParseStatusVO> parseStatus(@PathVariable String id) {
        return Result.ok(datasetService.getParseStatus(id));
    }

    /** 创建数据集。 */
    @PostMapping
    public Result<DatasetVO> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @Valid @RequestBody CreateDatasetRequest req) {
        return Result.ok(datasetService.create(req, userId));
    }

    /** 更新自己的数据集。 */
    @PutMapping("/{id}")
    public Result<DatasetVO> update(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id,
            @Valid @RequestBody UpdateDatasetRequest req) {
        return Result.ok(datasetService.update(id, req, userId));
    }

    /** 删除自己的数据集。 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id) {
        datasetService.remove(id, userId);
        return Result.ok();
    }

    /**
     * 上传 CSV 到自己的数据集(multipart,字段名 file)。
     * 立即返回 parseStatus=UPLOADED,后台异步解析回填 fieldsSchema/recordCount。
     */
    @PutMapping(value = "/{id}/file", consumes = "multipart/form-data")
    public Result<DatasetVO> uploadFile(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) String userId,
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        return Result.ok(datasetService.uploadCsv(id, userId, file));
    }
}
