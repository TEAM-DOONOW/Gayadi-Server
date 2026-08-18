# QWEN.md — Gayadi-Server

## 프로젝트

GAYADI 백엔드 API. Kotlin + Spring Boot 4.1 + Gradle + Spring AI 1.0.
Android 앱(`Gayadi-Android`)과 동일한 GAYADI 브랜드, 한국어 UI 텍스트.

## 핵심 규칙

- **모듈 구조 유지**: `auth`, `travel`, `survey`, `schedule`, `place`, `event`, `route`, `recommendation`, `common`, `config`
- **JdbcClient + raw SQL** — JPA/Hibernate 도입 금지 (현재 MVP 계약)
- **ApiException(HttpStatus, 메시지)** — 비즈니스 오류는 이걸로 throw, 메시지는 한국어
- **data class DTO** — 요청/응답은 Kotlin data class, 검증은 `@field:` 어노테이션
- **Flyway** — 스키마 변경은 `V{N}__{description}.sql` 마이그레이션으로만
- **조건부 AI** — `OPENAI_API_KEY` 없을 때도 기존 API 전부 정상 동작해야 함
- **기존 API 계약 유지** — 엔드포인트 경로·응답 구조를 깨는 변경 금지

## 빌드

```bash
./gradlew clean build     # 전체 (Windows: .\gradlew.bat)
./gradlew test            # 테스트만
./gradlew bootRun         # 로컬 실행 (H2 인메모리)
```

WSL에 Java 없음 → 빌드/테스트는 Windows에서 실행.

## 커밋 규약

`<type>/#<issue>: <subject>` — 한국어 50자 이내, 명사 종결.
브랜치: `<type>/#<issue>-<description>` (kebab-case).

## 아키텍처 메모

- Airflow(별도 Python 프로젝트)가 공공 API 수집 → 정제 → Embedding → Vector DB 적재
- 이 서버는 Vector DB 조회 + LLM 추천 (서빙) 담당
- `RouteProvider` 인터페이스로 외부 경로 API 교체 가능 (현재 LocalRouteProvider 스텁)
- `PlanService.value(row, key)` — JdbcClient 행에서 대소문자 무관 키 조회
