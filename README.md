# Gayadi Server

GAYADI는 여행자의 성향과 날씨·혼잡·교통 변화를 반영해 여행 전·중·후의 일정과 이동 경로를 추천하는 여행 도우미 서비스입니다.

- 여행 전: 그룹 성향 설문을 바탕으로 장소와 일정을 생성합니다.
- 출발 전: `GROUP_MEETING` 또는 `INDIVIDUAL` 방식에 맞는 대중교통 경로를 추천합니다.
- 여행 중: 날씨·혼잡·교통 이벤트가 일정에 영향을 주면 대안을 만들고, 사용자 승인 후 미래 일정만 수정합니다.
- 여행 후: 마지막 장소에서 멤버별 귀가 목적지까지 `RETURN` 경로를 추천합니다.

## 현재 구현

Spring Boot 4.1과 Java 21 기반의 실행 가능한 modular monolith MVP입니다.

- 모듈: `auth`, `travel`, `survey`, `schedule`, `place`, `event`, `route`, `common`
- 로컬 DB: H2 메모리 DB + Flyway
- 운영 DB: PostgreSQL 환경변수 설정
- 외부 경로 API: `RouteProvider` 포트와 결정적 로컬 스텁 구현
- 운영 확인: Actuator health/info/metrics
- 일정 변경: 현재 `trip_plan`을 유지하고 `revision_no` 증가 + `change_proposals` 감사 기록

외부 관광·날씨·혼잡·대중교통 API가 없어도 로컬에서 전체 핵심 흐름을 실행할 수 있습니다. 운영 연동 시 각 로컬 어댑터를 실제 공급자 어댑터로 교체합니다.

## 실행 방법

필수 환경은 JDK 21입니다. Maven은 Wrapper가 포함되어 있어 별도 설치가 필요 없습니다.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

기동 후 확인:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:8080/api/v1/surveys/personality
GET http://localhost:8080/api/v1/places
```

로컬 기본 설정은 H2 메모리 DB를 사용합니다. 운영에서는 `.env.example`을 참고해 다음 환경변수를 주입합니다.

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://localhost:5432/gayadi
DB_USERNAME=gayadi
DB_PASSWORD=...
```

## 핵심 API

| 단계 | API | 설명 |
| --- | --- | --- |
| 사용자 | `POST /api/v1/users` | 로컬 개발용 사용자 생성 |
| 여행 전 | `POST /api/v1/trips` | 여행·출발 방식·소유자 이동 정보 생성 |
| 여행 전 | `POST /api/v1/trips/{tripId}/members` | 멤버 출발지·귀가지 등록 |
| 여행 전 | `POST /api/v1/trips/{tripId}/survey-responses` | 멤버 성향 설문 제출 |
| 여행 전 | `POST /api/v1/trips/{tripId}/plan/generate` | 그룹 성향 기반 현재 일정 생성 |
| 출발 | `POST /api/v1/trips/{tripId}/routes/recommend?phase=DEPARTURE&memberId=...` | 멤버/그룹 출발 경로 추천 |
| 여행 중 | `POST /api/v1/trips/{tripId}/start` | 여행 시작 |
| 여행 중 | `POST /api/v1/trips/{tripId}/event-observations` | 날씨·혼잡·교통 관측 및 변경안 생성 |
| 여행 중 | `POST /api/v1/trips/{tripId}/change-proposals/{proposalId}/decision` | 변경안 승인·거절 |
| 여행 후 | `POST /api/v1/trips/{tripId}/routes/recommend?phase=RETURN&memberId=...` | 멤버별 귀가 경로 추천 |
| 여행 후 | `POST /api/v1/trips/{tripId}/complete` | 여행 완료 |

`TravelFlowIntegrationTests`가 사용자 생성부터 설문, 일정, 모여서 출발, 이벤트 변경 승인, revision 증가, 귀가 및 완료까지 검증합니다.

## 데이터 원칙

- 여행 소유자는 생성과 동시에 여행 멤버가 됩니다.
- `GROUP_MEETING`은 `meetingAt`과 `meetingLocation`이 필수입니다.
- 같은 여행에서 멤버별 최신 성향 응답 하나만 그룹 집계에 반영합니다.
- 여행당 현재 일정은 하나이며, 재생성·승인 시 revision만 증가합니다.
- 변경 제안의 `baseRevisionNo`가 현재 일정과 다르면 HTTP 409로 거절합니다.
- `RETURN` 경로는 항상 멤버별 경로입니다.
- 외부 API 장애가 있어도 확정 일정과 저장 경로는 DB에서 조회할 수 있도록 설계합니다.

## 문서와 발표 자료

- [서비스 설계서](docs/architecture/gayadi-service-design.md)
- [ERDCloud Import SQL](docs/database/gayadi-erdcloud.sql)
- [발표용 ERD PNG](docs/presentation/gayadi-erd-presentation.png) / [SVG](docs/presentation/gayadi-erd-presentation.svg)
- [발표용 서비스 아키텍처 PNG](docs/presentation/gayadi-service-architecture-presentation.png) / [SVG](docs/presentation/gayadi-service-architecture-presentation.svg)
- [발표용 서비스 흐름도 PNG](docs/presentation/gayadi-service-flow-presentation.png) / [SVG](docs/presentation/gayadi-service-flow-presentation.svg)

세 그림은 각각 데이터 구조, 서버 구성, 여행 전·중·후 사용자 흐름을 설명합니다. 서비스 아키텍처의 실선은 현재 MVP에 구현된 연결이고, 점선은 실제 출시 전에 공급자 API와 연결할 영역입니다. 발표 이미지는 `scripts/generate-presentation-diagrams.cjs`로 재생성할 수 있습니다.

## 아직 운영 연동이 필요한 범위

- OAuth/OIDC 토큰 검증과 여행 멤버 권한을 principal 기반으로 전환
- 관광·날씨·혼잡·대중교통 공급자 API 어댑터
- Redis 기반 경로 후보 TTL 캐시
- FCM/SSE 알림
- PostgreSQL Testcontainers 통합 테스트와 CI/CD

이 항목들은 모듈 경계와 교체 포인트를 유지하되, 로컬 서비스 기동을 막지 않도록 분리했습니다.
