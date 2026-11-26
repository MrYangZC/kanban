package com.seven7m.kanban.common.exception;

/**
 * 业务异常：业务校验失败时抛出，码与文案来自 {@link ErrorCode}。
 *
 * <p>构造方式统一成「错误码 + 可变参数」：
 * <pre>{@code
 * throw new ServiceException(ErrorCode.USER_NOT_FOUND);
 * throw new ServiceException(ErrorCode.WORK_ITEM_TYPE_NOT_FOUND, typeId);
 * throw new ServiceException(ErrorCode.STATUS_TRANSITION_DENIED, fromName, targetName);
 * }</pre>
 * 占位符由 {@code ErrorCode} 的文案声明，异常只负责格式化，避免调用点各写一遍字符串拼接。
 */
public class ServiceException extends BaseException {

    /** 命中的错误码；便于上层按语义分支（例如测试断言），而不是比对中文文案 */
    private final ErrorCode errorCode;

    public ServiceException(ErrorCode errorCode, Object... args) {
        super(errorCode.getCode(), format(errorCode, args));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 用占位符填充文案。
     *
     * <p>没有参数时直接返回默认文案，不跑 {@link String#format}：
     * 文案里若出现百分号（如「100% 完成」）会被当成格式说明符而抛异常。
     */
    private static String format(ErrorCode errorCode, Object[] args) {
        if (args == null || args.length == 0) {
            return errorCode.getMessage();
        }
        return String.format(errorCode.getMessage(), args);
    }
}
