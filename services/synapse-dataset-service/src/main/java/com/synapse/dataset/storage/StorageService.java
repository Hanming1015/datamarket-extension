package com.synapse.dataset.storage;

import java.io.InputStream;

/**
 * 对象存储抽象(当前实现为 MinIO)。接口化以便将来替换为 S3/OSS 而不动业务代码。
 */
public interface StorageService {

    /** 上传对象,返回对象键。 */
    String put(String objectKey, InputStream stream, long size, String contentType);

    /** 下载对象为输入流(调用方负责关闭)。 */
    InputStream get(String objectKey);

    /** 删除对象(不存在则忽略)。 */
    void remove(String objectKey);
}
