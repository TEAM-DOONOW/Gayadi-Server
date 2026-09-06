# GAYADI 서버 문서 안내

이 문서는 GAYADI 서버 문서의 최상위 진입점이다. 새 개발자와 인수인계 담당자는 여기서 시작해 목적에 맞는 문서를 확인한다.

> 새 작업 세션은 프로젝트 개요·설계·개발 표준·API 계약과 담당 기능을 먼저 이해한 뒤, [개발 인수인계](HANDOFF.md)에 따라 진행 방향을 사용자에게 확인한다.

## 처음 읽는 순서

1. 루트 [`README.md`](../README.md)에서 기술 스택, 실행 방법과 핵심 API를 확인한다.
2. [서비스 설계서](architecture/gayadi-service-design.md)에서 제품 흐름과 핵심 도메인을 이해한다.
3. [개발 표준](development-standards/README.md)에서 패키지, 계층과 코드 작성 규칙을 확인한다.
4. [프런트엔드 API 명세](FRONTEND_API_SPEC.md)와 Swagger UI에서 실제 API 계약을 확인한다.
5. 담당 기능에 따라 아래 세부 문서와 실제 코드를 확인한다.
6. [개발 인수인계](HANDOFF.md)에 따라 권장 로드맵을 이어갈지 다른 우선 작업을 진행할지 사용자에게 확인한다.

## 문서 지도

| 영역 | 문서 | 용도 |
|---|---|---|
| 인수인계 | [개발 인수인계](HANDOFF.md) | 새 세션의 필수 확인·질문·종료 절차 |
| 개발 표준 | [개발 표준 안내](development-standards/README.md) | 모든 서버 개발자가 지켜야 할 구조·코드·검증 규약 |
| 개발 로드맵 | [다음 개발 권장 순서](development-standards/next-steps.md) | 인증부터 도메인별 API 완성도를 높이는 권장 순서 |
| 아키텍처 | [서비스 설계서](architecture/gayadi-service-design.md) | 서비스 흐름, 도메인과 데이터 설계의 기준 |
| 아키텍처 | [도메인과 AI 검색](architecture/domain-and-ai-search.md) | 관계형 도메인과 추천 검색 경계 |
| 아키텍처 | [Agent 조사](architecture/agent-research.md) | AI Agent 적용 배경과 판단 근거 |
| Android 연동 | [Android 기능 계약](architecture/android-feature-contract.md) | Android 기능과 서버 계약 |
| API | [프런트엔드 API 명세](FRONTEND_API_SPEC.md) | 클라이언트 연동용 경로와 요청·응답 |
| 예외 | [예외 처리 가이드](exception/README.md) | 공통 오류 응답, ErrorCode, i18n과 테스트 |
| 보안 | [보안 문서 안내](security/README.md) | 현재 HTTP·JWT 보안 구조와 Redis·RTR·민감정보 강화 기준 |
| 데이터베이스 | [ERD SQL](database/gayadi-erdcloud.sql) | 데이터 모델 확인과 ERDCloud 반영 원본 |
| 외부 API | [TourAPI 매뉴얼](tourapi-manual/manual_v4.4.txt) | 한국관광공사 API 연동 참고 자료 |
| 발표 자료 | [발표 자료 안내](presentation/README.md) | ERD·아키텍처·서비스 흐름 이미지와 편집 원본 |

## 작업별 문서 사용법

### API를 추가하거나 변경할 때

1. [패키지와 계층 구조](development-standards/package-structure.md)
2. [DTO 작성 기준](development-standards/dto.md)
3. [예외 처리 가이드](exception/README.md)
4. [개발 절차](development-standards/development-workflow.md)
5. 구현 후 `FRONTEND_API_SPEC.md`와 OpenAPI 계약 갱신

### DB 조회나 저장을 변경할 때

1. [Repository 작성 기준](development-standards/repository.md)
2. Flyway 마이그레이션과 [ERD SQL](database/gayadi-erdcloud.sql) 확인
3. H2 및 PostgreSQL 차이, 제약조건과 잠금 동작 검증

### 오류·Validation 메시지를 추가할 때

1. [오류 코드 작성 기준](exception/error-code.md)
2. [예외 매핑](exception/exception-mapping.md)
3. [다국어 메시지](exception/i18n.md)
4. [예외 테스트](exception/testing.md)

### 코드 리뷰와 작업 완료 전

1. [Java 코드 작성 형식](development-standards/code-style.md)
2. [코드 품질 점검](development-standards/code-quality.md)
3. [개발 절차의 완료 조건](development-standards/development-workflow.md#완료-조건)

### 다음 구현 대상을 정할 때

1. [다음 개발 권장 순서](development-standards/next-steps.md)에서 선행 도메인 완료 여부 확인
2. 해당 도메인의 전체 API와 예외·권한·DB 검증 범위 결정
3. 하나의 완결된 사용자 흐름을 작업 또는 PR 단위로 선택

## 문서 관리 원칙

- 문서는 현재 구현을 설명하며 완료된 작업의 진행 일지는 기준 문서에 남기지 않는다.
- API 계약, 패키지 구조나 개발 규칙이 바뀌면 코드와 문서를 같은 변경에서 수정한다.
- 중복 설명은 최소화하고 세부 내용은 해당 영역 문서로 연결한다.
- 비밀값, 운영 접속 정보와 개인정보는 문서에 기록하지 않는다.
- 외부 원본 자료는 기준 문서와 구분하고, 프로젝트 판단은 Markdown 문서에 요약한다.

## 인수인계 체크리스트

- 현재 브랜치와 미커밋 변경 범위를 확인한다.
- 루트 README의 실행 방법으로 Java 21 환경에서 애플리케이션을 구동한다.
- 담당 도메인의 Controller → Service → Repository → Query Result 흐름을 확인한다.
- Swagger UI(`/api/docs`)와 OpenAPI JSON(`/api/openapi`)으로 API 계약을 확인한다.
- 자동 테스트와 스타일 검사를 실행한다.
- 남은 작업, 알려진 제약과 외부 연동 상태를 이슈 또는 PR에 기록한다.

이 체크리스트는 특정 리팩토링 세션의 상태가 아니라 언제든 새로운 개발자가 프로젝트를 이어받기 위한 공통 시작점이다.
