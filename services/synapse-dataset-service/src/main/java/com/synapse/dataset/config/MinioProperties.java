package com.synapse.dataset.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 Nacos 里的 {@code synapse.minio.*} 配置。
 */
@Data
@ConfigurationProperties(prefix = "synapse.minio")
public class MinioProperties {

    /** S3 端点,如 http://127.0.0.1:9000。 */
    private String endpoint;

    private String accessKey;

    private String secretKey;

    /** 数据集 CSV 原始文件桶(启动时自动建)。 */
    private String bucket;
}
