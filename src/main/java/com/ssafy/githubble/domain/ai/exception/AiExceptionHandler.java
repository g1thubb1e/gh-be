package com.ssafy.githubble.domain.ai.exception;

import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.dto.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeoutException;

@Slf4j
@RestControllerAdvice
public class AiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        int fieldErrorCount = ex.getBindingResult().getFieldErrorCount();
        int globalErrorCount = ex.getBindingResult().getGlobalErrorCount();
        log.warn("ai.exception.event event=validation_failed fieldErrorCount={} globalErrorCount={} errorType={}",
                fieldErrorCount, globalErrorCount, ex.getClass().getName());
        return ApiResult.error(ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex) {
        log.warn("ai.exception.event event=response_status status={} errorType={}",
                ex.getStatusCode(), ex.getClass().getName());

        return ApiResult.error(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<?> handleTimeout(TimeoutException ex) {
        log.error("ai.exception.event event=timeout errorType={}", ex.getClass().getName());

        return ApiResult.error(ErrorCode.AI_TIMEOUT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        if (isTimeoutCause(ex)) {
            return handleTimeout(new TimeoutException());
        }
        log.error("ai.exception.event event=unexpected errorType={}", ex.getClass().getName());
        return ApiResult.error(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private boolean isTimeoutCause(Throwable ex) {
        Throwable cause = ex;
        for (int i = 0; i < 5 && cause != null; i++) {
            if (cause instanceof TimeoutException
                    || cause.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
