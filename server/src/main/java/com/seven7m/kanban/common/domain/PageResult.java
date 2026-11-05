package com.seven7m.kanban.common.domain;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页返回体：总数 + 当前页 + 每页条数 + 数据。
 *
 * <p>之所以把 pageNum/pageSize 一起回传：前端做「跳到第 N 页」和「切换每页条数」时
 * 需要知道服务端实际生效的值（入参会被 clamp，例如 size 上限 100），只回 list 会让前端
 * 误判自己请求的参数就是生效参数。
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private long pageNum;
    private long pageSize;
    private List<T> list;

    public PageResult() {
    }

    public PageResult(long total, long pageNum, long pageSize, List<T> list) {
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.list = list;
    }
}
