package com.seven7m.kanban.common.exception;

/**
 * 业务异常：用于业务逻辑校验失败，code 复用 HTTP 语义（400/403/404...）。
 */
public class ServiceException extends BaseException {

    public ServiceException(String message) {
        super(400, message);
    }

    public ServiceException(int code, String message) {
        super(code, message);
    }
}
