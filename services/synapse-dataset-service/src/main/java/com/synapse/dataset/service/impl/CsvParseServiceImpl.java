package com.synapse.dataset.service.impl;

import com.synapse.dataset.entity.Dataset;
import com.synapse.dataset.entity.FieldSchema;
import com.synapse.dataset.mapper.DatasetMapper;
import com.synapse.dataset.service.CsvParseService;
import com.synapse.dataset.storage.StorageService;
import com.synapse.dataset.util.SchemaInferer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认 {@link CsvParseService}。在独立线程解析 CSV:
 * PARSING → 采样推断字段结构 + 统计行数 → READY(失败置 FAILED),完成后失效缓存。
 */
@Service
public class CsvParseServiceImpl implements CsvParseService {

    private static final Logger log = LoggerFactory.getLogger(CsvParseServiceImpl.class);

    /** 类型推断的采样行数上限(全量统计行数,仅采样前 N 行推断类型)。 */
    private static final int SAMPLE_LIMIT = 200;

    @Autowired
    private DatasetMapper datasetMapper;

    @Autowired
    private StorageService storageService;

    @Autowired
    private CacheManager cacheManager;

    @Async
    @Override
    public void parseAsync(String datasetId) {
        Dataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null || dataset.getFileObjectKey() == null) {
            log.warn("parseAsync skipped, dataset or file missing: {}", datasetId);
            return;
        }

        markStatus(dataset, "PARSING");

        //下载→解码→解析→推断→落库,任何一步炸(MinIO 挂、编码错、CSV 格式坏)都会跳出去被外层 catch 接住,置成 FAILED
        try (InputStream is = storageService.get(dataset.getFileObjectKey());
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            List<List<String>> sample = new ArrayList<>();
            int total = 0;
            for (CSVRecord rec : parser) {
                total++;
                if (sample.size() < SAMPLE_LIMIT) {
                    List<String> row = new ArrayList<>(rec.size());
                    for (int i = 0; i < rec.size(); i++) {
                        row.add(rec.get(i));
                    }
                    sample.add(row);
                }
            }

            List<FieldSchema> schema = SchemaInferer.infer(headers, sample);
            dataset.setFieldsSchema(schema);
            dataset.setFields(schema.stream().map(FieldSchema::getName).toList());
            dataset.setRecordCount(total);
            dataset.setParseStatus("READY");
            datasetMapper.updateById(dataset);
            evictCache(datasetId);
            log.info("CSV parsed: dataset={} columns={} rows={}", datasetId, headers.size(), total);

        } catch (Exception e) {
            log.error("CSV parse failed: dataset={}", datasetId, e);
            markStatus(dataset, "FAILED");
        }
    }

    private void markStatus(Dataset dataset, String status) {
        dataset.setParseStatus(status);
        datasetMapper.updateById(dataset);
        evictCache(dataset.getId());
    }

    /** 解析改动了字段结构,失效数据集详情缓存。 */
    private void evictCache(String datasetId) {
        var cache = cacheManager.getCache("dataset");
        if (cache != null) {
            cache.evict(datasetId);
        }
    }
}
