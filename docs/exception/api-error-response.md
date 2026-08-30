# API 오류 응답

모든 MVC 및 Spring Security 오류는 `ApiErrorResponse` 형식으로 반환한다.

```json
{
  "timestamp": "2026-08-30T12:34:56.789Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/v1/trips/1/expenses",
  "traceId": "c52aa0d7-43d5-4df5-b90e-78042612c357",
  "details": [
    { "field": "amount", "message": "경비 금액이 필수입니다." }
  ]
}
```

## 필드

| 필드 | 의미 |
|---|---|
| `timestamp` | 오류 발생 시각(UTC ISO-8601) |
| `status` | HTTP 상태 코드 |
| `code` | 클라이언트 분기용 안정적인 오류 코드 |
| `message` | 사용자에게 표시할 안전한 안내 문구 |
| `path` | Query String을 제외한 요청 경로 |
| `traceId` | 서버 로그 추적 식별자 |
| `details` | 필드·파라미터별 오류 목록, 없으면 `null` |

Validation 상세 정보에는 사용자가 제출한 원본 값인 `rejectedValue`를 포함하지 않는다.

## OpenAPI 모델

서비스 공통 모델은 `common.response.ApiErrorResponse`이다. OpenAPI 설정은 이 모델을 참조하며, 외부 라이브러리의 오류 DTO와 서비스 응답 DTO를 혼용하지 않는다.
