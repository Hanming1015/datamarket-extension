package com.synapse.dataset;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 数据集服务启动类。
 * {@link EnableAsync} 开启异步:CSV 解析在后台线程执行(见 CsvParseService)。
 */
@EnableAsync
@SpringBootApplication
public class DatasetApplication {
    public static void main(String[] args) {
        SpringApplication.run(DatasetApplication.class, args);
    }
}
