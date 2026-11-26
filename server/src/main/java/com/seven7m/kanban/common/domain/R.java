package com.seven7m.kanban.common.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构（沿用 yushun-cloud 语义）：
 * code=200 成功，其余复用 HTTP 语义码（400/401/403/404/405/500）。
 *
 * <p>成功码与文案用常量表达，避免"200"/"操作成功"这两个字面量散落在各处调用点。
 */
@Data
public class R<T> implements Serializable {

    /** 成功码 */
    public static final int SUCCESS_CODE = 200;
    /** 成功默认文案 */
    public static final String SUCCESS_MESSAGE = "操作成功";

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
        return new R<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(SUCCESS_CODE, msg, data);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(int code, String msg, T data) {
        return new R<>(code, msg, data);
    }
}
