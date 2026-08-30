# 예외 처리 가이드

GAYADI API는 모든 실패 응답을 안정적인 오류 코드와 공통 JSON 형식으로 반환한다. 애플리케이션 예외는 `BusinessException(ErrorCode)`로 표현하며, HTTP 응답 생성은 전역 예외 처리기가 담당한다.

## 문서 구성

| 문서 | 내용 |
|---|---|
| [API 오류 응답](api-error-response.md) | 공통 JSON 필드와 응답 예시 |
| [오류 코드와 비즈니스 예외](error-code.md) | ErrorCode 작성·사용 규칙 |
| [예외 매핑](exception-mapping.md) | MVC, Validation, Security 예외 변환 |
| [도메인 오류 코드](domain-error-codes.md) | 도메인별 ErrorCode 위치와 책임 |
| [테스트](testing.md) | 오류 계약 검증 범위와 실행 방법 |
| [다국어 메시지](i18n.md) | 도메인별 한국어·영어 메시지 구성과 사용 기준 |

민감정보 저장과 로그 마스킹 기준은 [민감정보 보호](../security/sensitive-data-protection.md)를 참고한다.

## 처리 흐름

```text
Controller / Service
        |
        v
BusinessException(ErrorCode)
        |
        v
GlobalExceptionHandler
        |
        v
ApiErrorResponse
```

Spring Security 필터에서 발생한 401·403은 `ApiAuthenticationEntryPoint`와 `ApiAccessDeniedHandler`가 같은 응답 형식으로 작성한다.

## 핵심 원칙

- 클라이언트는 `message`가 아니라 `code`로 분기한다.
- 서비스는 `ResponseEntity`나 임의의 오류 `Map`을 만들지 않는다.
- 사용자 입력과 예상 가능한 상태 충돌만 `BusinessException`으로 표현한다.
- 외부 API 원문 오류와 내부 예외 메시지는 응답에 노출하지 않는다.
- 성공 응답 형식은 오류 응답 계약과 별도로 관리한다.
- DTO 단일 필드 검증은 Bean Validation을 우선 사용한다.
- 여러 필드의 관계, DB 상태, 권한, 외부 연동 실패는 서비스 계층에서 검증한다.
