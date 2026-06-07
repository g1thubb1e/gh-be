package com.ssafy.githubble.global.exception;

import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.dto.ApiResult;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<String>> handleBusinessException(BusinessException e) {
        log.warn("global.exception.event event=business_error code={} errorType={}",
                e.getErrorCode().name(), e.getClass().getName());
        return ApiResult.error(e.getErrorCode());
    }

    // 알 수 없는 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e) {
        log.error("global.exception.event event=unexpected errorType={}", e.getClass().getName());
        return ApiResult.error(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
