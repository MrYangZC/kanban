package com.seven7m.kanban.common.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构（沿用 yushun-cloud 语义）：
 * code=200 成功，其余复用 HTTP 语义码（400/401/403/404/405/500）
 */
@Data
public class R<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(200, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(200, msg, data);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(int code, String msg, T data) {
        return new R<>(code, msg, data);
    }
}
