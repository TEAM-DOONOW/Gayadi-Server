# QWEN.md — Gayadi-Server

## 프로젝트

GAYADI 백엔드 API. Java 21 + Spring Boot 4.1 + Gradle + Spring AI 2.0.
Android 앱(`Gayadi-Android`)과 동일한 GAYADI 브랜드, 한국어 UI 텍스트.

상세 작업 규칙은 `AGENTS.md`를 우선하고, 구현 현황은 `HANDOFF.md`를 확인한다.

## 핵심 규칙

- **모듈 구조 유지**: `auth`, `travel`, `survey`, `schedule`, `place`, `event`, `route`, `recommendation`, `common`, `config`
- **PostgreSQL 단일 원본** — Firebase/Firestore 및 Android 로컬 저장을 업무 저장소로 사용 금지
- **Spring Data JPA 우선** — 신규 도메인 쓰기는 JPA/Hibernate, 기존 JdbcClient는 점진적으로 이전
- **ApiException(HttpStatus, 메시지)** — 비즈니스 오류는 이걸로 throw, 메시지는 한국어
- **DTO 분리** — 요청/응답 Java record와 영속 엔티티를 분리하고 Jakarta Validation 적용
- **Flyway** — 스키마 변경은 `V{N}__{description}.sql` 마이그레이션으로만
- **조건부 AI** — `OPENAI_API_KEY` 없을 때도 기존 API 전부 정상 동작해야 함
- **기존 API 계약 유지** — 엔드포인트 경로·응답 구조를 깨는 변경 금지

## 빌드

```bash
./gradlew clean build     # 전체 (Windows: .\gradlew.bat)
./gradlew test            # 테스트만
./gradlew bootRun         # 로컬 PostgreSQL로 실행
```

WSL에 Java 없음 → 빌드/테스트는 Windows에서 실행.

## 커밋 규약

`<type>/#<issue>: <subject>` — 한국어 50자 이내, 명사 종결.
브랜치: `<type>/#<issue>-<description>` (kebab-case).

## 아키텍처 메모

- 현재 추천은 TourAPI 후보를 조회한 뒤 선택하는 API-first Chat Agent이며 임베딩과 Vector DB는 미구현
- `RouteProvider` 인터페이스로 외부 경로 API 교체 가능
- `PlanService.value(row, key)` — JdbcClient 행에서 대소문자 무관 키 조회
