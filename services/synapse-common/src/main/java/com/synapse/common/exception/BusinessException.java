package com.synapse.common.exception;

import com.synapse.common.api.ResultCode;
import lombok.Getter;

/**
 * Thrown for expected business-rule violations. Carries a result code so the
 * global handler can translate it into a {@code Result} without leaking stack traces.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
