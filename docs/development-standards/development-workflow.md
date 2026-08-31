# 개발 절차

이 문서는 신규 API, 기존 기능 변경과 도메인 확장에 공통으로 적용하는 작업 순서를 정의한다.

## 작업 시작

1. `git status --short`로 기존 작업을 확인하고 관련 없는 변경을 보존한다.
2. 서비스 설계, 프런트엔드 API 명세와 담당 도메인 코드를 확인한다.
3. 기존 API 경로, 상태 코드, JSON 필드와 권한 조건을 테스트로 고정한다.
4. DB 스키마 변경이 필요하면 기존 Flyway 이력과 PostgreSQL 호환성을 확인한다.

## 구현 순서

1. API 동작별 Request DTO를 `dto/request`에 둔다.
2. 필수·선택값과 형식 Validation을 정의하고 한국어·영어 메시지 키를 추가한다.
3. Response DTO를 `dto/response`에 두고 공개 JSON 계약을 명확히 한다.
4. 고정된 상태와 분류는 `model` enum 또는 값 타입으로 표현한다.
5. DB 조회 결과는 `query/*QueryResult`로 분리한다.
6. Repository가 SQL, Row 매핑과 저장 형식 변환을 담당하게 한다.
7. Service에는 권한, 업무 규칙, 트랜잭션과 응답 변환을 둔다.
8. Controller에는 인증 사용자, `@Valid`, HTTP 상태와 OpenAPI 설명만 둔다.
9. 예외는 도메인 `ErrorCode`와 `BusinessException`으로 공통 응답에 연결한다.
10. 테스트와 관련 문서를 함께 수정한다.

필요하지 않은 Command, Mapper, Repository 인터페이스나 빈 패키지를 형식적으로 만들지 않는다. 현재 복잡도에서 책임을 분명하게 만드는 최소 구조를 선택한다.

## 완료 조건

- Controller 내부에 재사용 가능한 Request·Response 타입을 중첩 선언하지 않는다.
- 공개 API가 `Map<String, Object>`나 DB 객체를 반환하지 않는다.
- Service가 `JdbcClient`, SQL과 DB 컬럼명을 알지 못한다.
- Repository 밖으로 DB Row Map이 노출되지 않는다.
- Request Validation이 i18n 메시지 키를 사용한다.
- OpenAPI가 실제 DTO와 필수·nullable·enum 계약을 표현한다.
- 기존 또는 변경된 API 계약을 자동 테스트로 검증한다.
- 주요 타입과 공개 기능에 역할 중심 설명이 있다.
- Java 21 빌드, 전체 테스트, 스타일 검사와 diff 검사가 통과한다.

## 검증 명령

```powershell
python scripts/check_java_layout.py
.\gradlew.bat compileJava "-Dorg.gradle.java.installations.paths=C:\Progra~1\Eclipse~1\jdk-21.0.12.101-hotspot" --no-daemon --console=plain
.\gradlew.bat test "-Dorg.gradle.java.installations.paths=C:\Progra~1\Eclipse~1\jdk-21.0.12.101-hotspot" --no-daemon --console=plain
git diff --check
```

Windows 환경에서 Wrapper 종료 코드와 Gradle 출력이 다르면 `BUILD SUCCESSFUL` 또는 실제 실패 내용을 기준으로 판단한다.

## 별도 설계 검토가 필요한 변경

다음 변경은 일반 기능 개발에 포함해 일괄 적용하지 않는다.

- JDBC Repository의 JPA 일괄 전환
- 성공 응답 공통 래퍼 도입
- 기존 API 경로 또는 JSON 필드의 호환성 파괴
- 물리적 멀티 모듈·마이크로서비스 분리
- 인증, 암호화 또는 개인정보 처리 정책 변경
- 운영 DB 스키마의 파괴적 변경

이러한 변경은 영향 범위, 마이그레이션과 롤백 방법을 별도 문서와 테스트로 먼저 합의한다.
