# 가야디 서버

가야디는 여행자의 성향과 날씨·혼잡·교통 변화를 반영해 일정과 이동 경로를 관리하는 여행 도우미 서비스입니다.

- 여행 전: 그룹 성향 설문을 바탕으로 장소와 일정을 생성합니다.
- 출발 전: 함께 출발하거나 따로 출발하는 방식에 맞춰 예상 경로를 추천합니다.
- 여행 중: 날씨·혼잡·교통 이벤트가 일정에 영향을 주면 대안을 만들고, 사용자 승인 후 미래 일정만 수정합니다.
- 여행 후: 마지막 장소에서 멤버별 귀가 목적지까지 `RETURN` 경로를 추천합니다.
- 장소 추천: 선택 기능으로 분리되어 있으며, OpenAI 키가 없는 환경에서도 여행·일정 API는 정상 동작합니다.

## 기술 스택

- **언어**: Java 21
- **프레임워크**: Spring Boot 4.1
- **빌드**: Gradle
- **선택 기능**: Spring AI 2.0(OpenAI 대화·임베딩)
- **DB**: H2(로컬) / PostgreSQL(운영)
- **마이그레이션**: Flyway

## 아키텍처

```
[자료 수집]
관광·날씨·축제 자료 수집 → 정제 → 검색 자료 저장

[API 서버 — Spring Boot]
Android 요청 → 인증·권한 검사 → PostgreSQL 조회·트랜잭션 → 응답
```

## 현재 구현

Spring Boot 4.1 기반의 단일 서버입니다.

- 모듈: `auth`, `travel`, `survey`, `schedule`, `place`, `event`, `route`, `common`, `recommendation`, `config`
- 로컬 DB: H2 메모리 DB + Flyway
- 운영 DB: PostgreSQL
- 경로: `RouteProvider` 교체 지점과 직선거리 기반 예상값 제공
- AI 추천: `OPENAI_API_KEY` 설정 시 활성화하며 검색 벡터는 로컬 파일에 저장 (없어도 기존 API는 정상 동작)
- 운영 확인: Actuator health/info
- 일정 변경: 일정 버전과 변경 제안 기록으로 동시 수정 충돌 방지
- Android 연계: 프로필, 9문항 성향검사, 여행, 참여자, 공유·개별 초대, 일정, 찜, 약관 제공
- API 문서: `/api/docs`, OpenAPI JSON `/api/openapi`

외부 관광·날씨·혼잡·대중교통 API가 없어도 로컬에서 전체 핵심 흐름을 실행할 수 있습니다.

## 실행 방법

필수 환경은 JDK 21입니다. Gradle Wrapper가 포함되어 있어 별도 설치가 필요 없습니다.

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

기동 후 확인:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8080/api/v1/surveys/travel-personality-v1
GET http://localhost:8080/api/v1/places
GET http://localhost:8080/api/docs
```

로컬 기본 설정은 H2 메모리 DB를 사용합니다. 운영에서는 `.env.example`을 참고해 다음 환경변수를 주입합니다.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://localhost:5432/gayadi
DB_USERNAME=gayadi
DB_PASSWORD=...
OPENAI_API_KEY=sk-...
APP_AI_ENABLED=true
AI_CHAT_MODEL=openai
AI_EMBEDDING_MODEL=openai
```

## 핵심 API

| 단계 | API | 설명 |
| --- | --- | --- |
| 인증 | `POST /api/v1/auth/registrations` | 이메일·비밀번호·닉네임으로 계정 등록 |
| 인증 | `POST /api/v1/auth/tokens` | 로그인 토큰 발급 |
| 사용자 | `GET/PATCH/DELETE /api/v1/users/current` | 내 프로필 조회·수정·탈퇴 |
| 설문 | `GET /api/v1/surveys/travel-personality-v1` | 9문항과 8개 성향 결과 조회 |
| 설문 | `POST /api/v1/surveys/travel-personality-v1/submissions` | 내 성향 답변 제출 |
| 여행 전 | `GET/POST /api/v1/trips` | 내 여행 목록·생성 |
| 여행 전 | `GET/PATCH/DELETE /api/v1/trips/{tripId}` | 여행 상세·수정·삭제 |
| 여행 전 | `GET /api/v1/trips/{tripId}/participants` | 참여자 목록 |
| 여행 전 | `PUT/DELETE /api/v1/trips/{tripId}/participants/{userId}` | 참여자 관리 |
| 여행 전 | `GET/POST /api/v1/trips/{tripId}/invitations` | 여행 초대 관리 |
| 여행 전 | `PATCH /api/v1/trips/{tripId}/invitations/{invitationId}` | 개별 초대 상태 변경 |
| 여행 전 | `POST /api/v1/trip-memberships` | 초대 코드로 참여 |
| 여행 전 | `POST /api/v1/trips/{tripId}/survey-responses` | 멤버 성향 설문 제출 |
| 여행 전 | `GET/POST /api/v1/trips/{tripId}/plans` | 그룹 성향 기반 일정 조회·생성 |
| 일정 | `GET/POST /api/v1/trips/{tripId}/schedules` | 앱 일정 조회·추가 |
| 일정 | `PATCH/DELETE /api/v1/trips/{tripId}/schedules/{scheduleId}` | 앱 일정 수정·삭제 |
| 일정 | `PATCH /api/v1/trips/{tripId}/schedule-orders` | 일정 순서 변경 |
| 출발·귀가 | `POST /api/v1/trips/{tripId}/route-recommendations` | 경로 추천 |
| 경로 | `GET /api/v1/trips/{tripId}/route-selections` | 선택한 추천 경로 조회 |
| 경로 | `PUT/DELETE /api/v1/trips/{tripId}/route-selections/{type}` | 추천 경로 선택 관리 |
| 친구 | `GET/POST /api/v1/friendships` | 친구 관계 조회·요청 |
| 친구 | `PATCH/DELETE /api/v1/friendships/{friendshipId}` | 친구 상태 변경·삭제 |
| 여행 홈 | `GET /api/v1/trips/{tripId}/dashboard` | 여행·참여자·일정·변경 제안 집계 |
| 여행 상태 | `PATCH /api/v1/trips/{tripId}/status` | 여행 시작·완료 |
| 여행 중 | `POST /api/v1/trips/{tripId}/event-observations` | 날씨·혼잡·교통 관측 및 변경안 생성 |
| 여행 중 | `PATCH /api/v1/trips/{tripId}/change-proposals/{proposalId}` | 변경안 승인·거절 |
| 장소 | `GET /api/v1/places` | 검색·지역·분류·커서 기반 공개 장소 조회 |
| 찜 | `GET/PUT/DELETE /api/v1/users/current/favorite-places` | 내 장소 찜 관리 |
| 문서 | `GET /api/v1/legal-documents/{documentId}` | 약관·개인정보처리방침 조회 |
| 선택 기능 | `POST /api/v1/recommendations/places` | OpenAI 기반 장소 추천 |
| 관리 | `POST /api/v1/admin/place-embeddings` | 관리자 전용 장소 검색 자료 갱신 |

`TravelFlowIntegrationTests`가 사용자 생성부터 설문, 일정, 모여서 출발, 이벤트 변경 승인, revision 증가, 귀가 및 완료까지 검증합니다.

## 데이터 원칙

- 여행 소유자는 생성과 동시에 여행 멤버가 됩니다.
- 여행 소유자와 참여자 권한은 토큰의 사용자 식별자로 판단합니다.
- 같은 여행·일차에는 일정표를 하나만 저장합니다.
- 여행 공유 코드는 6자리이며 여러 참여자가 쓸 수 있습니다. 특정 사용자 초대는 8자리이고 만료와 한 번만 수락하는 조건을 DB 트랜잭션으로 확인합니다.
- 일정 재생성과 변경 승인은 일정 버전을 비교합니다.
- 변경 제안의 `baseRevisionNo`가 현재 일정과 다르면 HTTP 409로 거절합니다.
- `RETURN` 경로는 항상 멤버별 경로입니다.
- 외부 API 장애가 있어도 확정 일정과 저장 경로는 DB에서 조회할 수 있도록 설계합니다.

## 문서와 발표 자료

- [서비스 설계서](docs/architecture/gayadi-service-design.md)
- [ERDCloud Import SQL](docs/database/gayadi-erdcloud.sql)
- [발표용 ERD PNG](docs/presentation/gayadi-erd-presentation.png) / [SVG](docs/presentation/gayadi-erd-presentation.svg)
- [발표용 서비스 아키텍처 PNG](docs/presentation/gayadi-service-architecture-presentation.png) / [SVG](docs/presentation/gayadi-service-architecture-presentation.svg) / [draw.io 편집 원본](docs/presentation/gayadi-service-architecture.drawio)
- [발표용 서비스 흐름도 PNG](docs/presentation/gayadi-service-flow-presentation.png) / [SVG](docs/presentation/gayadi-service-flow-presentation.svg)

## 아직 운영 연동이 필요한 범위

- OAuth/OIDC 토큰 검증으로 전환 (현재는 로컬 JWT 기반 인증)
- 관광·날씨·혼잡·대중교통 공급자 API 어댑터
- 공공 API 수집 → 정제 → 검색 자료 저장 작업
- Redis 기반 경로 후보 TTL 캐시
- FCM/SSE 알림
- PostgreSQL Testcontainers 통합 테스트와 CI/CD

이 항목들은 모듈 경계와 교체 포인트를 유지하되, 로컬 서비스 기동을 막지 않도록 분리했습니다.
