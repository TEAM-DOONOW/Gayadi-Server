# 예외 처리 테스트

## 검증 범위

- 공통 JSON 필드와 HTTP 상태
- Validation의 필드별 `details`
- 401·403 Security 응답 형식
- 내부 메시지와 민감한 입력값 비노출
- 도메인별 상태와 공개 오류 코드
- 전체 ErrorCode의 `code` 중복 여부
- 전체 ErrorCode 메시지 키의 한국어·영어 번들 누락 및 치환 인자 일치 여부
- 외부 API 오류와 응답 파싱 실패 매핑

## 실행

```shell
./gradlew test
```

프로젝트 Java toolchain은 Java 21을 사용한다. 테스트 실패 시 HTTP 상태뿐 아니라 `code`, `details`, 민감정보 비노출 여부를 함께 확인한다.

주요 계약 테스트는 `ApiErrorResponseIntegrationTests`, `ApiSecurityHandlersTest`, `ApiErrorResponseFactoryTest`,
`ErrorCodeUniquenessTest`, `MessageBundleCompletenessTest`이다.
