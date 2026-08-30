# 예외 매핑

## MVC 예외

| 발생 원인 | 공개 코드 |
|---|---|
| `BusinessException` | 예외가 가진 도메인 ErrorCode |
| DTO·파라미터 Validation | `INVALID_REQUEST` |
| 파라미터 타입 불일치 | `INVALID_PARAMETER_TYPE` |
| 필수 파라미터·Part 누락 | `MISSING_REQUIRED_PARAMETER` |
| 잘못된 JSON | `MALFORMED_REQUEST_BODY` |
| 지원하지 않는 Content-Type | `UNSUPPORTED_MEDIA_TYPE` |
| 없는 경로 | `RESOURCE_NOT_FOUND` |
| 지원하지 않는 HTTP 메서드 | `METHOD_NOT_ALLOWED` |
| DB 제약 충돌 | `DATA_CONFLICT` |
| 처리되지 않은 예외 | `INTERNAL_SERVER_ERROR` |

5xx 비즈니스·외부 연동 오류와 예상하지 못한 예외는 `traceId`와 함께 서버 로그에 기록한다. 응답에는 스택 트레이스와 내부 예외 메시지를 포함하지 않는다.

## Security 예외

| 상황 | 상태 | 코드 |
|---|---:|---|
| 로그인 토큰 없음·무효 | 401 | `UNAUTHENTICATED` |
| 인증됐지만 권한 부족 | 403 | `ACCESS_DENIED` |

Security 필터는 MVC 전역 처리기를 통과하지 않으므로 `ApiSecurityErrorWriter`가 동일한 `ApiErrorResponse`를 생성한다.

## Validation 역할

- `@NotBlank`, `@Size`, `@Pattern`, `@Positive` 등 단일 값 규칙은 DTO에 둔다.
- 시작일과 종료일 같은 교차 필드 규칙은 서비스 또는 전용 Validator에서 처리한다.
- DB 조회가 필요한 존재·상태·권한 검증은 서비스에서 ErrorCode로 처리한다.
