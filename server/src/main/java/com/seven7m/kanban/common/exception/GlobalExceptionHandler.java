package com.seven7m.kanban.common.exception;

import com.seven7m.kanban.common.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：所有异常最终都收敛为 R<T>（code 复用 HTTP 语义）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：沿用其声明的 HTTP 语义码 */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.warn("业务异常[{}]: {}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@RequestBody + @Valid）：400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(HttpStatus.BAD_REQUEST.value(), msg);
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(HttpStatus.BAD_REQUEST.value(), msg);
    }

    /** 缺少请求参数（如 @RequestParam 缺失）：400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(HttpStatus.BAD_REQUEST.value(), "缺少参数：" + e.getParameterName());
    }

    /** 参数类型不匹配（如 UUID 非法）：400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(HttpStatus.BAD_REQUEST.value(), "参数类型错误：" + e.getName());
    }

    /** 请求体无法解析（JSON 格式错误）：400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return R.fail(HttpStatus.BAD_REQUEST.value(), "请求体格式错误");
    }

    /** 兜底：未知异常 500，仅返回通用文案，不泄露内部细节（SQL/堆栈等） */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常", e);
        return R.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "系统异常，请稍后重试");
    }
}
