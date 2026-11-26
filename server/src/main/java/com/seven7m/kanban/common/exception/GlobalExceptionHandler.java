package com.seven7m.kanban.common.exception;

import com.seven7m.kanban.common.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：所有异常最终都收敛为 R&lt;T&gt;。
 *
 * <p>码与文案全部来自 {@link ErrorCode}，不再在 handler 里手写 {@code R.fail(403, "...")}——
 * 否则同一条"无权限"文案会在异常处理与业务代码里各写一份，改一处漏一处。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 把错误码转成统一响应体 */
    private static <T> R<T> fail(ErrorCode errorCode) {
        return R.fail(errorCode.getCode(), errorCode.getMessage());
    }

    private static <T> R<T> fail(ErrorCode errorCode, String message) {
        return R.fail(errorCode.getCode(), message);
    }

    /** 业务异常：沿用其声明的 HTTP 语义码 */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.warn("业务异常[{}]: {}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 方法级鉴权失败（{@code @PreAuthorize} 抛出的 AuthorizationDeniedException）。
     *
     * <p>必须在兜底 Exception 之前被匹配到：这个异常是从方法调用处抛出的，
     * 走的是 ControllerAdvice 而不是 Security 的 ExceptionTranslationFilter，
     * 若不显式处理就会被兜底处理器当成系统故障返回 500——
     * 前端会把"没权限"误判成"服务出错"，运维也会收到无意义的错误告警。
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(org.springframework.security.access.AccessDeniedException e,
                                      HttpServletRequest request) {
        log.warn("权限不足 {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return fail(ErrorCode.FORBIDDEN);
    }

    /** 认证失败（如匿名访问被拦下）：401，与 SecurityConfig 的入口点文案保持一致 */
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleUnauthenticated(org.springframework.security.core.AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return fail(ErrorCode.UNAUTHORIZED);
    }

    /**
     * 把 Spring 默认的英文校验消息统一成本地化文案，并带上字段名。
     * 注解未显式声明 message 时会得到 "must not be blank" 这类英文，直接返回给前端不可读。
     */
    private String fieldMessage(FieldError fe) {
        String raw = fe.getDefaultMessage();
        String field = fe.getField();
        if (raw == null || raw.isBlank()) return "参数 " + field + " 不合法";
        return switch (raw) {
            case "must not be blank", "must not be null", "must not be empty" -> field + " 不能为空";
            case "must be a well-formed email address" -> field + " 不是合法的邮箱地址";
            case "must be true", "must be false" -> field + " 取值不合法";
            default -> {
                String lower = raw.toLowerCase();
                if (lower.startsWith("size must be between")) yield field + " 长度超出允许范围";
                if (lower.startsWith("must be less than") || lower.startsWith("must be greater than")
                        || lower.startsWith("must be less than or equal to")
                        || lower.startsWith("must be greater than or equal to")) {
                    yield field + " 数值超出允许范围";
                }
                if (lower.startsWith("must ")) yield field + " 参数不合法";
                yield raw;
            }
        };
    }

    /** 参数校验失败（@RequestBody + @Valid）：400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::fieldMessage)
                .collect(Collectors.joining("; "));
        return fail(ErrorCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::fieldMessage)
                .collect(Collectors.joining("; "));
        return fail(ErrorCode.BAD_REQUEST, msg);
    }

    /** 上传文件超过大小限制：给明确文案，避免落到兜底 500「系统异常」 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public R<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return fail(ErrorCode.UPLOAD_TOO_LARGE);
    }

    /** 缺少请求参数（如 @RequestParam 缺失）：400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return fail(ErrorCode.PARAM_MISSING, "缺少参数：" + e.getParameterName());
    }

    /** 参数类型不匹配（如 UUID 非法）：400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return fail(ErrorCode.PARAM_TYPE_MISMATCH, "参数类型错误：" + e.getName());
    }

    /** 请求体无法解析（JSON 格式错误）：400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return fail(ErrorCode.BODY_UNREADABLE);
    }

    /**
     * 不存在的接口路径：404。
     * 不加这条时，Spring 6.1 抛出的 NoResourceFoundException 会落到下面的兜底 Exception 分支，
     * 把「路径写错」报成 HTTP 500「系统异常」——污染监控告警，也让调用方误判为服务故障。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return fail(ErrorCode.API_NOT_FOUND);
    }

    /**
     * 数据库约束/长度/外键等数据完整性问题：400 明确提示，不落到兜底 500。
     * 典型场景：字段超长命中 varchar(n)、唯一键冲突等。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public R<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据约束异常: {}", e.getMostSpecificCause().getMessage());
        return fail(ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    /**
     * 兜底：未知异常 500，仅返回通用文案，不泄露内部细节（SQL/堆栈等）。
     * HTTP 状态码同步置为 500（而非一律 200），让网关/监控能按状态码识别故障；
     * 响应体仍是 R 结构（code=500 + 通用文案），前端拦截器对非 2xx 走 error 分支并 toast 同一文案。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常", e);
        return fail(ErrorCode.SYSTEM_ERROR);
    }
}
