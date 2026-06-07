package com.ssafy.githubble.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 400
    FAIL(HttpStatus.BAD_REQUEST, "요청이 실패했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    GITHUB_CONTENT_IS_DIRECTORY(HttpStatus.BAD_REQUEST, "파일이 아니라 디렉터리입니다."),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    GITHUB_PRIVATE_REPOSITORY_NOT_SUPPORTED(HttpStatus.FORBIDDEN, "현재는 public repository만 지원합니다."),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 502
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다."),
    EXTERNAL_API_UNAUTHORIZED(HttpStatus.BAD_GATEWAY, "외부 API 인증에 실패했습니다."),

    // 503
    AI_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "LLM 또는 임베딩 서비스 응답 시간을 초과했습니다. 잠시 후 재시도해주세요.");


    private final HttpStatus status;
    private final String message;
}
