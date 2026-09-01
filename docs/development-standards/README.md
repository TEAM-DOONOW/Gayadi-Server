# GAYADI 개발 표준

이 디렉터리는 GAYADI 서버를 신규 개발하거나 변경할 때 모든 개발자가 따라야 하는 기준을 정의한다. 과거 리팩토링 진행 기록이 아니라 현재 코드 구조를 기준으로 한 상시 규약이다.

## 문서 구성

| 문서 | 확인할 내용 |
|---|---|
| [패키지와 계층 구조](package-structure.md) | 도메인 패키지 구성과 계층별 책임 |
| [DTO 작성 기준](dto.md) | Request·Response, Validation, OpenAPI 계약 |
| [Repository 작성 기준](repository.md) | JDBC, Query Result, SQL과 트랜잭션 경계 |
| [Java 코드 작성 형식](code-style.md) | 어노테이션, record, 메서드와 주석 형식 |
| [코드 품질 점검](code-quality.md) | IDE 경고 판정과 정적·자동 검사 |
| [개발 절차](development-workflow.md) | 기능 추가·변경 순서와 완료 조건 |
| [다음 개발 권장 순서](next-steps.md) | 인증부터 도메인별 API를 완성·검증하는 로드맵 |

## 핵심 원칙

1. 도메인 중심 패키지를 유지하고 책임이 실제로 늘어날 때만 하위 패키지를 추가한다.
2. Controller는 HTTP 계약, Service는 업무 규칙, Repository는 영속성에 집중한다.
3. API DTO, 내부 Command, DB Query Result와 도메인 Model을 구분한다.
4. API 경로·상태 코드·JSON 계약을 테스트로 보호한다.
5. Validation·오류 메시지는 i18n 키를 사용하고 민감정보를 응답이나 로그에 노출하지 않는다.
6. 새 코드와 수정한 주변 코드는 동일한 형식과 문서화 기준을 적용한다.
7. 변경 완료 전 Java 21 빌드, 전체 테스트, 스타일 검사와 diff 검사를 수행한다.

## 빠른 확인 순서

처음 참여한 개발자는 다음 순서로 읽는다.

1. [전체 문서 안내](../README.md)
2. [서비스 설계](../architecture/gayadi-service-design.md)
3. [패키지와 계층 구조](package-structure.md)
4. 담당 계층에 맞는 DTO·Repository·코드 형식 문서
5. [개발 절차](development-workflow.md)
6. [다음 개발 권장 순서](next-steps.md)에서 담당 도메인의 검증 범위 확인

규약과 구현이 다르면 코드만 임의로 맞추지 않는다. 현재 API 계약과 테스트를 확인한 뒤 코드와 이 문서를 함께 갱신한다.
