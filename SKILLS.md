# SKILLS.md — Gayadi-Server Agent Guide

## Project Overview

**GAYADI Server**는 여행자 성향·날씨·혼잡·교통 변화를 반영해 일정과 이동 경로를 추천하는 백엔드 API입니다.

- 여행 전: 그룹 성향 설문 기반 장소·일정 생성
- 출발 전: GROUP_MEETING / INDIVIDUAL 대중교통 경로 추천
- 여행 중: 이벤트 관측 → 변경 제안 → 사용자 승인 → 미래 일정 수정
- 여행 후: 멤버별 귀가 경로 추천
- AI 추천: Spring AI + OpenAI RAG 기반 장소 추천

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1 + Java 21 |
| Framework | Spring Boot 4.1 |
| Build | Gradle 8.12 + Kotlin DSL |
| AI | Spring AI 1.0 (OpenAI Chat + Embedding) |
| Vector DB | SimpleVectorStore (로컬) / PgVector (운영) |
| DB | H2 (로컬) / PostgreSQL (운영) |
| Migration | Flyway |
| Test | JUnit 5 + AssertJ + SpringBootTest |

## Project Structure

```
src/main/kotlin/com/gayadi/server/
├── GayadiServerApplication.kt       # 진입점
├── config/
│   └── AiConfig.kt                  # Spring AI 조건부 설정
├── common/
│   ├── ApiException.kt              # HTTP 상태 + 메시지 예외
│   ├── ApiExceptionHandler.kt       # @RestControllerAdvice 전역 처리
│   ├── JsonSupport.kt               # ObjectMapper 래퍼
│   └── Location.kt                  # 위경도 + 라벨 data class
├── auth/
│   ├── UserController.kt            # POST /api/v1/users
│   └── UserService.kt
├── travel/
│   ├── TripController.kt            # POST/GET /api/v1/trips
│   ├── TripService.kt
│   ├── DepartureMode.kt             # GROUP_MEETING | INDIVIDUAL
│   └── TripStatus.kt                # DRAFT → READY → IN_PROGRESS → RETURNING → COMPLETED
├── survey/
│   ├── SurveyController.kt          # 설문 제출 + 그룹 성향
│   └── SurveyService.kt
├── schedule/
│   ├── PlanController.kt            # 일정 생성/조회
│   └── PlanService.kt
├── place/
│   ├── PlaceController.kt           # 장소 목록/상세
│   └── PlaceService.kt
├── event/
│   ├── EventController.kt           # 이벤트 관측 + 변경 제안 승인
│   └── EventService.kt
├── route/
│   ├── RouteController.kt           # 경로 추천
│   ├── RouteService.kt
│   ├── RouteProvider.kt             # 포트 인터페이스
│   └── LocalRouteProvider.kt        # 결정적 로컬 스텁
└── recommendation/
    ├── RecommendationController.kt  # POST /api/v1/recommendations/places
    ├── RecommendationService.kt     # RAG: VectorStore 검색 + ChatClient
    ├── PlaceEmbeddingService.kt     # 장소 → Vector DB 임베딩
    └── EmbeddingAdminController.kt  # POST /api/v1/admin/embed-places
```

## API Endpoints

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/users` | 사용자 생성 |
| GET | `/api/v1/users/{userId}` | 사용자 조회 |
| POST | `/api/v1/trips` | 여행 생성 |
| GET | `/api/v1/trips/{tripId}` | 여행 조회 |
| POST | `/api/v1/trips/{tripId}/members` | 멤버 추가 |
| POST | `/api/v1/trips/{tripId}/survey-responses` | 성향 설문 제출 |
| GET | `/api/v1/trips/{tripId}/personality-profile` | 그룹 성향 |
| POST | `/api/v1/trips/{tripId}/plan/generate` | 일정 생성 |
| GET | `/api/v1/trips/{tripId}/plan` | 일정 조회 |
| POST | `/api/v1/trips/{tripId}/event-observations` | 이벤트 관측 |
| GET | `/api/v1/trips/{tripId}/change-proposals` | 변경 제안 목록 |
| POST | `/api/v1/trips/{tripId}/change-proposals/{id}/decision` | 승인/거절 |
| POST | `/api/v1/trips/{tripId}/routes/recommend?phase=...` | 경로 추천 |
| POST | `/api/v1/trips/{tripId}/start` | 여행 시작 |
| POST | `/api/v1/trips/{tripId}/complete` | 여행 완료 |
| GET | `/api/v1/places` | 장소 목록 |
| GET | `/api/v1/places/{placeId}` | 장소 상세 |
| GET | `/api/v1/surveys/personality` | 설문 정의 |
| POST | `/api/v1/recommendations/places` | AI 장소 추천 (OpenAI 필요) |
| POST | `/api/v1/admin/embed-places` | 장소 임베딩 (OpenAI 필요) |

## Coding Conventions

1. **모듈 단위 패키지** — `auth`, `travel`, `survey`, `schedule`, `place`, `event`, `route`, `recommendation`, `common`, `config`
2. **Controller + Service 분리** — Controller는 요청/응답 DTO만, 비즈니스 로직은 Service
3. **JdbcClient 사용** — JPA/Hibernate 없음, `JdbcClient` + raw SQL
4. **data class DTO** — 요청/응답은 Kotlin data class, `@field:` 검증 어노테이션
5. **Map<String, Any> 응답** — 현재 MVP는 JdbcClient `listOfRows()` 결과를 직접 반환
6. **ApiException** — 비즈니스 오류는 `ApiException(HttpStatus, message)`으로 throw
7. **조건부 AI 빈** — `OPENAI_API_KEY` 없으면 recommendation 모듈 비활성화
8. **Flyway 마이그레이션** — `src/main/resources/db/migration/V{N}__{description}.sql`
9. **한국어 예외 메시지** — 사용자-facing 오류 메시지는 한국어

## Build & Test

```bash
./gradlew clean build        # 컴파일 + 테스트
./gradlew bootRun            # 로컬 실행 (H2)
./gradlew test               # 테스트만
```

- 로컬: H2 인메모리, Flyway 자동 마이그레이션
- 운영: `SPRING_PROFILES_ACTIVE=prod` + PostgreSQL 환경변수
- AI: `OPENAI_API_KEY` 설정 시 recommendation 엔드포인트 활성화

## Data Rules

- 여행 소유자는 생성과 동시에 OWNER 멤버
- GROUP_MEETING은 meetingAt + meetingLocation 필수
- 멤버별 최신 성향 응답 하나만 그룹 집계에 반영
- 여행당 현재 일정 하나, 재생성/승인 시 revision_no 증가
- 변경 제안의 baseRevisionNo 불일치 시 HTTP 409
- RETURN 경로는 항상 멤버별

## Agent Workflow

1. 수정 대상 파일을 먼저 읽어 현재 상태 파악
2. 기존 모듈 구조·네이밍·패턴 유지
3. 새 API 추가 시 Controller + Service + (필요 시) 마이그레이션
4. `./gradlew test`로 검증 (Windows: `.\gradlew.bat test`)
5. 커밋 형식: `<type>/#<issue>: <subject>` (한국어 50자 이내)
