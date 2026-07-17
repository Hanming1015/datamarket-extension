package com.synapse.common.exception;

import com.synapse.common.api.Result;
import com.synapse.common.api.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the unified {@link Result} envelope for every service.
 * Registered as a bean by {@code CommonAutoConfiguration}, so services get it for free.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("business exception: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "validation failed";
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /** @RequestParam / @PathVariable 上的约束(@Validated)校验失败。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("constraint violation: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /** 请求体缺失或 JSON 无法解析。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("malformed request body: {}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), "malformed request body");
    }

    /**
     * 缺少必填请求头。最常见于绕过网关直连、未带网关注入的 X-User-Id;
     * 语义上是"未认证",返回 401 而非 500。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public Result<Void> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("missing request header: {}", e.getHeaderName());
        return Result.fail(ResultCode.UNAUTHORIZED.getCode(), "missing required header: " + e.getHeaderName());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }
}
