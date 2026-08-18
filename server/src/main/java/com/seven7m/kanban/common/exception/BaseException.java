package com.seven7m.kanban.common.exception;

import lombok.Getter;

/**
 * 业务异常基类。携带 HTTP 语义码（复用 yushun-cloud 约定，不另设业务码段）。
 */
@Getter
public class BaseException extends RuntimeException {

    private final int code;

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
