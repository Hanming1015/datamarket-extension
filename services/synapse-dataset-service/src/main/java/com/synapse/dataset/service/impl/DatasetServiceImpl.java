package com.synapse.dataset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.synapse.common.api.ResultCode;
import com.synapse.common.exception.BusinessException;
import com.synapse.dataset.dto.CreateDatasetRequest;
import com.synapse.dataset.dto.UpdateDatasetRequest;
import com.synapse.dataset.entity.Dataset;
import com.synapse.dataset.entity.FieldSchema;
import com.synapse.dataset.mapper.DatasetMapper;
import com.synapse.dataset.service.CsvParseService;
import com.synapse.dataset.service.DatasetService;
import com.synapse.dataset.storage.StorageService;
import com.synapse.dataset.vo.DatasetSummaryVO;
import com.synapse.dataset.vo.DatasetVO;
import com.synapse.dataset.vo.PageResult;
import com.synapse.dataset.vo.ParseStatusVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 默认 {@link DatasetService}。迁移自单体 DatasetServiceImpl,
 * owner 改由网关 X-User-Id 注入(去掉 SecurityUtil);ownerName 的跨服务补全留到 Phase 3。
 */
@Service
public class DatasetServiceImpl implements DatasetService {

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private StorageService storageService;

    @Autowired
    private CsvParseService csvParseService;

    @Override
    public DatasetVO create(CreateDatasetRequest req, String ownerId) {
        Dataset dataset = new Dataset();
        dataset.setName(req.getName());
        dataset.setDescription(req.getDescription());
        dataset.setCategory(req.getCategory());
        applySchema(dataset, req.getFieldsSchema());
        dataset.setOwnerId(ownerId);
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setParseStatus("NONE");
        datasetMapper.insert(dataset);
        return toVO(dataset);
    }

    @Override
    public PageResult<DatasetSummaryVO> listOwn(String ownerId, long page, long size) {
        QueryWrapper<Dataset> qw = new QueryWrapper<>();
        qw.eq("owner_id", ownerId).orderByDesc("created_at");
        return toSummaryPage(datasetMapper.selectPage(new Page<>(page, size), qw));
    }

    @Override
    public PageResult<DatasetSummaryVO> listAll(long page, long size) {
        // 市场只列解析就绪的数据集,避免半成品/失败的数据集外露
        QueryWrapper<Dataset> qw = new QueryWrapper<>();
        qw.eq("parse_status", "READY").orderByDesc("created_at");
        return toSummaryPage(datasetMapper.selectPage(new Page<>(page, size), qw));
    }

    @Override
    @Cacheable(value = "dataset", key = "#id")
    public DatasetVO get(String id) {
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toVO(dataset);
    }

    @Override
    public ParseStatusVO getParseStatus(String id) {
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return new ParseStatusVO(dataset.getId(), dataset.getParseStatus(), dataset.getRecordCount());
    }

    @Override
    @CacheEvict(value = "dataset", key = "#id")
    public DatasetVO update(String id, UpdateDatasetRequest req, String ownerId) {
        Dataset dataset = mustOwn(id, ownerId);
        if (req.getName() != null) {
            dataset.setName(req.getName());
        }
        if (req.getDescription() != null) {
            dataset.setDescription(req.getDescription());
        }
        if (req.getCategory() != null) {
            dataset.setCategory(req.getCategory());
        }
        if (req.getFieldsSchema() != null) {
            applySchema(dataset, req.getFieldsSchema());
        }
        datasetMapper.updateById(dataset);
        return toVO(dataset);
    }

    @Override
    @CacheEvict(value = "dataset", key = "#id")
    public void remove(String id, String ownerId) {
        Dataset dataset = mustOwn(id, ownerId);
        // 顺带删除 MinIO 里的原始文件
        if (dataset.getFileObjectKey() != null) {
            storageService.remove(dataset.getFileObjectKey());
        }
        datasetMapper.deleteById(id);
    }

    @Override
    @CacheEvict(value = "dataset", key = "#datasetId")
    public DatasetVO uploadCsv(String datasetId, String ownerId, MultipartFile file) {
        Dataset dataset = mustOwn(datasetId, ownerId);
        validateCsv(file);
        String objectKey = datasetId + "/" + safeName(file.getOriginalFilename());
        try {
            storageService.put(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException("cannot read uploaded file: " + e.getMessage());
        }
        dataset.setFileObjectKey(objectKey);
        dataset.setFileName(file.getOriginalFilename());
        dataset.setFileSize(file.getSize());
        dataset.setParseStatus("UPLOADED");
        datasetMapper.updateById(dataset);
        // 异步解析:不阻塞上传响应
        csvParseService.parseAsync(datasetId);
        return toVO(dataset);
    }

    /** 上传前校验:非空 + 文件名是 .csv,不合格直接 400,别进存储/解析。 */
    private void validateCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "uploaded file is empty");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "only .csv files are accepted");
        }
    }

    /** 文件名去掉路径分隔符,防目标键被穿越。 */
    private String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "data.csv";
        }
        return original.replaceAll("[\\\\/]+", "_");
    }

    /** 加载并校验归属;不存在或非本人一律当作不存在(不泄露)。 */
    private Dataset mustOwn(String id, String ownerId) {
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null || !ownerId.equals(dataset.getOwnerId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return dataset;
    }

    /** 写入字段结构并同步派生列名列表。 */
    private void applySchema(Dataset dataset, List<FieldSchema> schema) {
        if (schema == null) {
            return;
        }
        dataset.setFieldsSchema(schema);
        dataset.setFields(schema.stream().map(FieldSchema::getName).toList());
    }

    private DatasetVO toVO(Dataset dataset) {
        DatasetVO vo = new DatasetVO();
        BeanUtils.copyProperties(dataset, vo);
        return vo;
    }

    /** 分页实体页 → 轻量摘要页(列表不带 fieldsSchema)。 */
    private PageResult<DatasetSummaryVO> toSummaryPage(IPage<Dataset> p) {
        List<DatasetSummaryVO> records = p.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private DatasetSummaryVO toSummary(Dataset dataset) {
        DatasetSummaryVO vo = new DatasetSummaryVO();
        BeanUtils.copyProperties(dataset, vo);
        return vo;
    }
}
