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
- **선택 기능**: Spring AI 2.0(Groq API-first Agent, 임베딩은 선택)
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

- 업무 모듈: `auth`, `travel`, `coordination`, `survey`, `schedule`, `place`, `favorite`, `invitation`, `friendship`, `expense`, `event`, `route`, `notice`, `support`, `legal`
- 어댑터·조합 모듈: `tourapi`, `weather`, `recommendation`, `dashboard`, `firebase`, `common`, `config`
- 로컬 DB: H2 메모리 DB + Flyway
- 운영 DB: PostgreSQL
- 경로: `RouteProvider` 교체 지점과 직선거리 기반 예상값 제공
- AI 추천: `GROQ_API_KEY`와 Groq 호환 설정 시 TourAPI 후보를 검색·판단하는 Agent 활성화 (없어도 기존 API는 정상 동작)
- 운영 확인: Actuator health/info
- 일정 변경: 일정 버전과 변경 제안 기록으로 동시 수정 충돌 방지
- Android 연계: 프로필, 9문항 성향검사, 여행, 날짜 조율, 참여자, 공유·개별 초대, 일정, 경로, 경비·공금·정산, 찜, 공지, 문의, 약관 제공
- API 문서: `/api/docs`, OpenAPI JSON `/api/openapi`
- Android 연동 명세: [`docs/FRONTEND_API_SPEC.md`](docs/FRONTEND_API_SPEC.md)

외부 관광·날씨·혼잡·대중교통 API가 없어도 로컬에서 전체 핵심 흐름을 실행할 수 있습니다. `.env`는 실행 디렉터리에서 자동으로 읽습니다.

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
GROQ_API_KEY=gsk_...
APP_AI_ENABLED=true
AI_CHAT_MODEL=openai
AI_LLM_BASE_URL=https://api.groq.com/openai/v1
AI_LLM_MODEL=openai/gpt-oss-20b
APP_AI_EMBEDDING_ENABLED=false
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
| 날짜 조율 | `GET /api/v1/trips/{tripId}/date-coordination` | 참여자별 제출 상태와 공통 가능 날짜 조회 |
| 날짜 조율 | `PUT /api/v1/trips/{tripId}/date-coordination/availability/current` | 현재 사용자의 가능한 날짜 제출 |
| 날짜 조율 | `PUT /api/v1/trips/{tripId}/date-coordination/finalized-dates` | 소유자가 공통 가능 범위로 여행 날짜 확정 |
| 일정 | `GET/POST /api/v1/trips/{tripId}/schedules` | 앱 일정 조회·추가 |
| 일정 | `PATCH/DELETE /api/v1/trips/{tripId}/schedules/{scheduleId}` | 앱 일정 수정·삭제 |
| 일정 | `PATCH /api/v1/trips/{tripId}/schedule-orders` | 일정 순서 변경 |
| 출발·귀가 | `POST /api/v1/trips/{tripId}/route-recommendations` | 경로 추천 |
| 경로 | `GET /api/v1/trips/{tripId}/route-selections` | 선택한 추천 경로 조회 |
| 경로 | `PUT/DELETE /api/v1/trips/{tripId}/route-selections/{type}` | 추천 경로 선택 관리 |
| 친구 | `GET/POST /api/v1/friendships` | 친구 관계 조회·요청 |
| 친구 | `PATCH/DELETE /api/v1/friendships/{friendshipId}` | 친구 상태 변경·삭제 |
| 경비 | `GET/POST /api/v1/trips/{tripId}/expenses` | 여행 지출 조회·추가 |
| 경비 | `PATCH/DELETE /api/v1/trips/{tripId}/expenses/{expenseId}` | 여행 지출 수정·삭제 |
| 정산 | `GET /api/v1/trips/{tripId}/expense-settlement` | 참여자별 부담액과 최소 송금안 조회 |
| 공금 | `GET /api/v1/trips/{tripId}/shared-fund` | 충전·사용·잔액 조회 |
| 공금 | `POST /api/v1/trips/{tripId}/shared-fund/contributions` | 공동 경비 충전 |
| 여행 홈 | `GET /api/v1/trips/{tripId}/dashboard` | 여행·참여자·일정·변경 제안 집계 |
| 여행 상태 | `PATCH /api/v1/trips/{tripId}/status` | 여행 시작·완료 |
| 여행 중 | `POST /api/v1/trips/{tripId}/event-observations` | 날씨·혼잡·교통 관측 및 변경안 생성 |
| 상황 대처 | `POST /api/v1/trips/{tripId}/situation-responses` | 여행 컨텍스트를 포함한 대체 장소·상황 대응. 여행 중에는 승인 가능한 변경안도 생성 |
| 여행 중 | `PATCH /api/v1/trips/{tripId}/change-proposals/{proposalId}` | 변경안 승인·거절 |
| 날씨 | `GET /api/v1/weather/now` | 인증 사용자의 기상청 초단기실황 조회 |
| 날씨 | `GET /api/v1/weather/ultra-forecast` | 인증 사용자의 6시간 이내 초단기예보 조회 |
| 날씨 | `GET /api/v1/weather/forecast` | 인증 사용자의 전체 단기예보 조회 |
| 날씨 | `GET /api/v1/weather/version` | 인증 사용자의 예보 파일 버전 조회 |
| 혼잡 | `GET /api/v1/congestion/forecast` | 한국관광공사 30일 관광지 집중률 예측. 자료가 없으면 낮은 신뢰도의 달력 추정값 반환 |
| 앱 장소 탐색 | `GET /api/v1/tour/discover` | GAYADI 지역명·여행일·카테고리로 관광지와 예상 혼잡도를 통합 조회 |
| 장소 | `GET /api/v1/places` | 검색·지역·분류·커서 기반 공개 장소 조회 |
| 찜 | `GET/PUT/DELETE /api/v1/users/current/favorite-places` | 내 장소 찜 관리 |
| 공지 | `GET /api/v1/notices`, `GET /api/v1/notices/{noticeId}` | 공개 공지 목록·상세 조회 |
| 문의 | `POST /api/v1/inquiries` | 인증 사용자의 고객지원 문의 접수 |
| 문서 | `GET /api/v1/legal-documents/{documentId}` | 약관·개인정보처리방침 조회 |
| 선택 기능 | `POST /api/v1/recommendations/places` | Groq·TourAPI 기반 장소 추천 Agent |
| 상황 대처 | `POST /api/v1/recommendations/situations` | 날씨·혼잡·교통·대중교통 누락을 반영한 장소 대안 |
| 관리 | `POST /api/v1/admin/place-embeddings` | 관리자 전용 장소 검색 자료 갱신 |

`TravelFlowIntegrationTests`가 사용자 생성부터 설문, 일정, 모여서 출발, 이벤트 변경 승인, revision 증가, 귀가 및 완료까지 검증합니다. `AndroidFeatureDomainHttpIntegrationTests`는 Android 화면의 날짜 조율, 공동 경비, 개인 지출, 정산, 공지와 문의를 실제 HTTP·JWT·Swagger 계약으로 검증합니다. `AiUserJourneyIntegrationTests`는 추천 결과가 상황 변화와 변경안 승인 후 일정·경로에 반영되는 사용자 경로를 검증합니다.

## 데이터 원칙

- 여행 소유자는 생성과 동시에 여행 멤버가 됩니다.
- 여행 소유자와 참여자 권한은 토큰의 사용자 식별자로 판단합니다.
- 같은 여행·일차에는 일정표를 하나만 저장합니다.
- 여행 공유 코드는 6자리이며 여러 참여자가 쓸 수 있습니다. 특정 사용자 초대는 8자리이고 만료와 한 번만 수락하는 조건을 DB 트랜잭션으로 확인합니다.
- 일정 재생성과 변경 승인은 일정 버전을 비교합니다.
- 여행 중 AI 상황 추천은 내부 장소로 저장된 후보만 변경안에 포함하며, 같은 일정 버전의 이전 AI 변경안은 만료시킵니다.
- 여행 상황 요청에서 날씨를 생략하면 현재 위치의 기상청 초단기실황을 자동으로 추천 정책에 반영합니다.
- 여행 상황 요청에서 혼잡을 생략하면 한국관광공사 관광지 집중률 예측을 반영하며, 키 권한이나 자료가 없으면 `CALENDAR_HEURISTIC`으로 명시한 낮은 신뢰도의 추정값을 사용합니다.
- 날씨 대안은 승인 시점에도 실내 장소인지 다시 확인하고, 혼잡·교통 대안은 여행 지역의 활성 장소인지 확인합니다.
- 변경 제안의 `baseRevisionNo`가 현재 일정과 다르면 HTTP 409로 거절합니다.
- `RETURN` 경로는 항상 멤버별 경로입니다.
- 외부 API 장애가 있어도 확정 일정과 저장 경로는 DB에서 조회할 수 있도록 설계합니다.
- 기상청 호출은 HTTPS를 사용하고 `totalCount`까지 모든 페이지를 합쳐 반환하며, 서버 API는 JWT 인증을 요구합니다.

## 문서와 발표 자료

- [서비스 설계서](docs/architecture/gayadi-service-design.md)
- [도메인 맵과 AI 검색 설계 초안](docs/architecture/domain-and-ai-search.md)
- [Agent 연구·개발 자료](docs/architecture/agent-research.md)
- [Android 기능과 서버 API 대응표](docs/architecture/android-feature-contract.md)
- [ERDCloud Import SQL](docs/database/gayadi-erdcloud.sql)
- [발표용 ERD PNG](docs/presentation/gayadi-erd-presentation.png) / [SVG](docs/presentation/gayadi-erd-presentation.svg)
- [발표용 서비스 아키텍처 PNG](docs/presentation/gayadi-service-architecture-presentation.png) / [SVG](docs/presentation/gayadi-service-architecture-presentation.svg) / [draw.io 편집 원본](docs/presentation/gayadi-service-architecture.drawio)
- [발표용 서비스 흐름도 PNG](docs/presentation/gayadi-service-flow-presentation.png) / [SVG](docs/presentation/gayadi-service-flow-presentation.svg)

## 아직 운영 연동이 필요한 범위

- OAuth/OIDC 토큰 검증으로 전환 (현재는 로컬 JWT 기반 인증)
- 서울 주요 121장소 실시간 혼잡 공급자 추가와 TMAP 운영 키 검증
- 공공 API 수집 → 정제 → 검색 자료 저장 작업
- Redis 기반 경로 후보 TTL 캐시
- FCM/SSE 알림
- Android의 파일·Firestore 저장소를 서버 API 저장소로 교체하고 로그인 토큰을 연결하는 작업
- PostgreSQL Testcontainers 통합 테스트와 CI/CD

이 항목들은 모듈 경계와 교체 포인트를 유지하되, 로컬 서비스 기동을 막지 않도록 분리했습니다.
