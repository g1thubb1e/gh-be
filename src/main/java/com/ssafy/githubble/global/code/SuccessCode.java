package com.ssafy.githubble.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {

    SUCCESS(HttpStatus.OK, "요청이 성공했습니다."),
    CREATED(HttpStatus.CREATED, "리소스가 생성되었습니다."),
    UPDATED(HttpStatus.OK, "수정이 완료되었습니다."),
    DELETED(HttpStatus.OK, "삭제가 완료되었습니다.");

    private final HttpStatus status;
    private final String message;
}
