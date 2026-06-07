package com.ssafy.githubble.global.dto;

import com.ssafy.githubble.global.code.ErrorCode;
import com.ssafy.githubble.global.code.SuccessCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public record ApiResult<T>(
        String status,
        String message,
        T data
) {
    // 기본 헤더 상태코드 200
    public static <T> ResponseEntity<ApiResult<T>> success() {
        return ResponseEntity.ok(new ApiResult<>("SUCCESS", "요청이 성공", null));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(HttpHeaders headers) {
        return ResponseEntity.ok().headers(headers).body(new ApiResult<>("SUCCESS", "요청이 성공", null));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(T data) {
        return ResponseEntity.ok(new ApiResult<>("SUCCESS", "요청이 성공", data));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(T data, HttpHeaders headers) {
        return ResponseEntity.ok().headers(headers).body(new ApiResult<>("SUCCESS", "요청이 성공", data));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(SuccessCode code) {
        return ResponseEntity.status(code.getStatus()).body(new ApiResult<>(code.name(), code.getMessage(), null));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(SuccessCode code, T data) {
        return ResponseEntity.status(code.getStatus()).body(new ApiResult<>(code.name(), code.getMessage(), data));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(SuccessCode code, HttpHeaders headers) {
        return ResponseEntity.status(code.getStatus()).headers(headers).body(new ApiResult<>(code.name(), code.getMessage(), null));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(SuccessCode code, T data, HttpHeaders headers) {
        return ResponseEntity.status(code.getStatus()).headers(headers).body(new ApiResult<>(code.name(), code.getMessage(), data));
    }


    // 기본 헤더 상태코드 400
    public static <T> ResponseEntity<ApiResult<T>> error() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResult<>("ERROR","요청이 실패", null));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(T data) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResult<>("ERROR","요청이 실패", data));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(HttpHeaders headers) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(new ApiResult<>("ERROR","요청이 실패", null));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(T data, HttpHeaders headers) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(new ApiResult<>("ERROR","요청이 실패", data));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(new ApiResult<>(code.name(), code.getMessage(), null));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code, HttpHeaders headers) {
        return ResponseEntity.status(code.getStatus()).headers(headers).body(new ApiResult<>(code.name(), code.getMessage(), null));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code, T data) {
        return ResponseEntity.status(code.getStatus()).body(new ApiResult<>(code.name(), code.getMessage(), data));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code, T data, HttpHeaders headers) {
        return ResponseEntity.status(code.getStatus()).headers(headers).body(new ApiResult<>(code.name(), code.getMessage(), data));
    }
}
