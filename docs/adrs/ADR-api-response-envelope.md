# ADR: 공통 API 응답 Envelope 사용

- Status: Accepted
- Date: 2026-04-22

## Context

성공/실패마다 응답 구조가 달라지면 프론트엔드 방어 로직이 늘어난다.  
또한 HTTP 상태 코드와 바디 구조가 일관되지 않으면 모니터링과 로그 해석이 어려워진다.

## Decision

- 모든 API의 응답 바디는 `ApiResult<T>` 형태로 통일한다.
- Controller와 ExceptionHandler의 최종 리턴 타입은 `ResponseEntity<ApiResult<T>>`를 기본으로 한다.
- 응답 바디를 반환하는 API는 `Set-Cookie` 등 응답 헤더가 필요하더라도 `ResponseEntity<ApiResult<T>>`를 유지한다.
- HTTP 상태 코드는 비즈니스 결과와 맞게 사용한다.
- 응답 바디의 `status`는 성공/실패 여부 문자열이 아니라, 프론트 분기와 모니터링을 위한 **결과 코드**로 사용한다.
- 별도의 `errorCode` 필드는 두지 않는다. 실패 응답의 세부 코드는 `status`에 `ErrorCode.name()`을 담아 표현한다.
- 성공/실패 여부는 HTTP 상태 코드의 2xx 여부로 판단하고, `status`는 세부 결과 코드로 사용한다.

## Standard Shape

```java
public record ApiResult<T>(
    String status,      // SuccessCode.name() or ErrorCode.name()
    String message,
    T data
) {
    public static <T> ResponseEntity<ApiResult<T>> success(T data) {
        return ResponseEntity.ok(new ApiResult<>("SUCCESS", "요청이 성공", data));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(T data, HttpHeaders headers) {
        return ResponseEntity.ok()
            .headers(headers)
            .body(new ApiResult<>("SUCCESS", "요청이 성공", data));
    }

    public static <T> ResponseEntity<ApiResult<T>> success(SuccessCode code, T data, HttpHeaders headers) {
        return ResponseEntity.status(code.getStatus())
            .headers(headers)
            .body(new ApiResult<>(code.name(), code.getMessage(), data));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code) {
        return ResponseEntity.status(code.getStatus())
            .body(new ApiResult<>(code.name(), code.getMessage(), null));
    }

    public static <T> ResponseEntity<ApiResult<T>> error(ErrorCode code, T data, HttpHeaders headers) {
        return ResponseEntity.status(code.getStatus())
            .headers(headers)
            .body(new ApiResult<>(code.name(), code.getMessage(), data));
    }
}
```

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResult<DiagramDetail>> getDiagram(@PathVariable String archId) {
    DiagramDetail data = diagramService.getDetail(archId);
    return ApiResult.success(data);
}
```

### Response Body Examples

성공 응답:

```json
{
  "status": "SUCCESS",
  "message": "요청이 성공",
  "data": {
    "archId": "owner__repo"
  }
}
```

실패 응답:

```json
{
  "status": "NOT_FOUND",
  "message": "대상을 찾을 수 없습니다.",
  "data": null
}
```

## HTTP Status Principle

| 상황 | 권장 HTTP 상태 |
|---|---|
| 정상 처리 | `200 OK` |
| 잘못된 요청 | `400 BAD_REQUEST` |
| 인증 필요 | `401 UNAUTHORIZED` |
| 권한 없음 | `403 FORBIDDEN` |
| 대상 없음 | `404 NOT_FOUND` |
| 충돌 | `409 CONFLICT` |
| 외부 API 오류 | `502 BAD_GATEWAY` |
| 서버 내부 오류 | `500 INTERNAL_SERVER_ERROR` |

## Consequences

### 장점
- 프론트가 성공/실패 구조를 예측 가능하게 처리할 수 있다.
- Grafana 등에서 4xx/5xx 비율만으로도 상태를 해석하기 쉬워진다.
- 로그 파싱과 검색이 쉬워진다.

### 단점
- 단순 응답도 envelope로 감싸야 해서 코드가 조금 길어진다.
- 제네릭 타입 선언이 길어질 수 있다.

## Notes

비즈니스 예외는 `ErrorCode`의 HTTP 상태 코드와 메시지를 사용해 `ApiResult.error(...)`로 변환한다.
`status`에는 `ErrorCode.name()`을 담는다.
