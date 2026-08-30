# 오류 코드와 비즈니스 예외

## ErrorCode 계약

| 속성 | 용도 |
|---|---|
| `status` | HTTP 상태 |
| `code` | API 전체에서 유일한 공개 코드 |
| `messageKey` | i18n 메시지 키 |

사용자에게 보여줄 문구는 ErrorCode에 작성하지 않고 도메인별 한국어·영어 메시지 번들에서 관리한다. 코드는 `DOMAIN_REASON` 형태의 대문자 스네이크 케이스를 사용한다. enum 내부 상수는 기능별로 묶고 `// English Group - 한글 설명` 주석을 붙인다.

```java
// Login - 로그인 자격 증명과 시도 제한
AUTH_INVALID_CREDENTIALS(...),
AUTH_LOGIN_RATE_LIMITED(...),
```

```java
AUTH_INVALID_CREDENTIALS(
        HttpStatus.UNAUTHORIZED,
        "AUTH_INVALID_CREDENTIALS",
        "error.auth.invalid-credentials"
)
```

## 사용 방법

```java
throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
throw new BusinessException(TripErrorCode.TRIP_STATUS_TRANSITION_INVALID, before, after);
```

동적 문구는 `{0}`, `{1}` 템플릿과 메시지 인자를 사용한다.

## 선택 기준

- 다른 도메인이 소유한 실패는 해당 도메인의 기존 ErrorCode를 재사용한다.
- 내부 불변식 위반과 프로그래밍 오류는 임의의 4xx 코드로 바꾸지 않는다.
- 외부 API의 인증·제한·장애·잘못된 응답은 구분하되 제공자의 원문은 노출하지 않는다.
- 공개 코드 중복은 `ErrorCodeUniquenessTest`가 방지한다.
- 메시지 키 누락은 `MessageBundleCompletenessTest`가 방지한다.
