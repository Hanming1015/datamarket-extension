package com.synapse.dataset.storage.impl;

import com.synapse.common.exception.BusinessException;
import com.synapse.dataset.config.MinioProperties;
import com.synapse.dataset.storage.StorageService;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * MinIO 实现。桶固定为 {@link MinioProperties#getBucket()}。
 */
@Service
public class MinioStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioProperties props;

    @Override
    public String put(String objectKey, InputStream stream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            return objectKey;
        } catch (Exception e) {
            log.error("MinIO put failed: {}", objectKey, e);
            throw new BusinessException("file upload failed: " + e.getMessage());
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("MinIO get failed: {}", objectKey, e);
            throw new BusinessException("file download failed: " + e.getMessage());
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            // 删除失败不阻断主流程
            log.warn("MinIO remove failed: {} ({})", objectKey, e.getMessage());
        }
    }
}
