package com.synapse.dataset.service;

/**
 * CSV 异步解析:后台读取已上传的 CSV,推断字段结构并回填数据集。
 * 实现见 {@link com.synapse.dataset.service.impl.CsvParseServiceImpl}。
 */
public interface CsvParseService {

    /** 异步解析指定数据集已上传的 CSV(不阻塞上传响应)。 */
    void parseAsync(String datasetId);
}
