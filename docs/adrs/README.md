# ADR 문서 목록

이 디렉토리는 백엔드 공통 구현 규약을 정리한 ADR 문서를 관리한다.

## 문서 목록

| 문서 | 내용 |
|---|---|
| `ADR-api-response-envelope.md` | API 응답 형식 통일 규약. `ApiResult<T>`, `ResponseEntity`, `status` 결과 코드 사용 원칙을 다룬다. |
| `ADR-business-exception.md` | 비즈니스 예외 처리 규약. `BusinessException`, `ErrorCode.name()` 기반 응답 코드, 글로벌 예외 처리 방식을 다룬다. |
| `ADR-http-only-cookie-auth.md` | 인증/인가 규약. Access Token, Refresh Token을 HttpOnly Cookie로 관리하는 방식을 다룬다. |
| `ADR-properties.md` | 환경변수 사용방법을 다룬다. |
| `ADR-sourcecode_caching.md` | github 소스코드 트리 캐시를 다룬다 |

## 추가 기준

인증, 응답 형식, 예외 처리, DB 스키마, 외부 API 호출 정책처럼 팀 전체가 따라야 하는 공통 구현 결정은 이 디렉토리에 ADR로 추가한다.