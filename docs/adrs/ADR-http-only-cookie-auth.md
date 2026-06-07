# ADR: HttpOnly Cookie 기반 인증

- Status: Accepted
- Date: 2026-04-22

## Context

백엔드와 프론트엔드가 동일한 인증 전제를 가져야 한다.  
토큰 저장 위치가 흔들리면 인증 흐름, CORS, 테스트 방식이 모두 달라진다.

## Decision

- `access token`, `refresh token`은 모두 **HttpOnly Cookie**에 저장한다.
- 기본 인증 저장소로 `localStorage`, `sessionStorage`를 가정하지 않는다.
- 인증 관련 기능은 쿠키 전제를 깨지 않는 방향으로 설계한다.

## Consequences

### 장점
- 프론트/백 모두 같은 인증 전제를 공유할 수 있다.
- 토큰을 JS에서 직접 읽지 않는 구조를 기본값으로 둘 수 있다.
- 테스트와 연동 흐름을 단순화할 수 있다.

### 단점
- 개발 환경에서 CORS와 쿠키 옵션 조정이 필요하다.
- `SameSite`, `Secure`, CSRF 대응 전략을 함께 정해야 한다.

## Notes

아래 항목은 구현 단계에서 별도 확정이 필요하다.

- `SameSite` 정책
- `Secure` 적용 조건
- refresh 재발급 시 쿠키 갱신 정책
- CSRF 대응 방식
