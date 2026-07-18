package com.synapse.dataset.vo;

import lombok.Data;

/**
 * 解析状态轻量视图,供上传后轮询用。
 * 走**不缓存**的读路径,保证轮询总能拿到最新状态(见 DatasetService#getParseStatus)。
 */
@Data
public class ParseStatusVO {

    private String id;
    /** NONE / UPLOADED / PARSING / READY / FAILED。 */
    private String parseStatus;
    private Integer recordCount;

    public ParseStatusVO() {
    }

    public ParseStatusVO(String id, String parseStatus, Integer recordCount) {
        this.id = id;
        this.parseStatus = parseStatus;
        this.recordCount = recordCount;
    }
}
