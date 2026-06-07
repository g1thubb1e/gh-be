# ADR: BusinessException + ErrorCode 기반 예외 처리

- Status: Accepted
- Date: 2026-04-22

## Context

단순 문자열 예외 메시지로는 비즈니스 맥락을 표현하기 어렵다.  
프론트엔드는 문자열 파싱이 아니라 **안정적인 에러 코드 기반 분기**가 필요하고, 로그/모니터링도 어떤 유형의 오류인지 식별 가능해야 한다.

## Decision

- 모든 비즈니스 예외는 `BusinessException` 기반으로 처리한다.
- `BusinessException`은 `ErrorCode`를 포함한다.
- `ErrorCode`는 최소한 아래 정보를 가진다.
  - HTTP Status
  - 사용자 메시지
- 예외는 `throw new BusinessException(ErrorCode.XYZ)` 형태로 던진다.
- 글로벌 예외 처리기(`@RestControllerAdvice`)에서 `ResponseEntity<ApiResult<?>>`로 변환한다.
- HTTP 상태 코드는 `ErrorCode`에 정의된 값을 그대로 사용한다.
- 응답 바디의 `status`에는 별도 문자열 필드가 아니라 `ErrorCode.name()`을 사용한다.
- 별도의 `errorCode` 응답 필드는 두지 않는다.

## Standard Shape

```java
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
```

```java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    FAIL(HttpStatus.BAD_REQUEST, "요청이 실패했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
```

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException e) {
        return ApiResult.error(e.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e) {
        log.error("알 수 없는 예외가 발생했습니다.", e);
        return ApiResult.error(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
```

## Consequences

### 장점
- 프론트가 HTTP 상태 코드와 `status` 값을 기준으로 분기할 수 있다.
- HTTP 상태 코드와 비즈니스 맥락을 동시에 표현할 수 있다.
- 장애 원인 추적과 로그 검색이 쉬워진다.
- 예외 흐름이 한 곳으로 수렴된다.

### 단점
- `ErrorCode` 설계와 관리 비용이 있다.
- 단순 로직에도 공통 예외 구조를 맞춰야 해서 초기 체감 비용이 있다.

## Notes

- 외부 API 예외도 가능하면 `ErrorCode`로 승격해 관리한다.
- 도메인별 세부 ErrorCode는 늘릴 수 있지만, 공통 응답 구조는 유지한다.
