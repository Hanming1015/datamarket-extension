package com.synapse.access.vo;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果包装(沿用 dataset-service 同名约定)。
 */
@Data
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.page = page;
        r.size = size;
        r.pages = size > 0 ? (total + size - 1) / size : 0;
        return r;
    }
}
