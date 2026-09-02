# GAYADI 개발 인수인계

이 문서는 새로운 개발자나 작업 세션이 프로젝트를 이해한 뒤, 실제 작업 방향을 결정하고 프로젝트를 이어받기 위한 절차다.

## 새 세션의 필수 시작 절차

새 세션은 [`docs/README.md`](README.md)의 처음 읽는 순서 1~5를 완료한 뒤 이 문서를 확인한다. 코드를 수정하기 전에는 반드시 다음 순서를 따른다.

1. [다음 개발 권장 순서](development-standards/next-steps.md)에서 현재 권장 작업을 확인한다.
2. `git status --short`로 기존 변경을 확인하고 사용자 작업을 임의로 되돌리지 않는다.
3. 현재 활성 파일과 관련 도메인의 Controller → Service → Repository → Query Result 흐름을 확인한다.
4. 문서와 실제 구현이 일치하는지 확인한다.
5. 최근 테스트 결과만 신뢰하지 말고 현재 작업 트리에서 필요한 검증 범위를 판단한다.
6. 파악한 프로젝트 상태를 바탕으로 실제 변경 전에 아래 우선순위 확인 질문을 사용자에게 한다.

> 프로젝트 구조와 현재 상태를 확인했습니다. 권장 로드맵은 `auth`부터 도메인별 API를 완성·검증하는 것입니다. 이 순서대로 진행할까요, 아니면 먼저 처리할 다른 기능·오류·우선 작업이 있나요?

사용자가 이미 이번 세션의 작업과 우선순위를 명확하게 지정했다면 같은 질문을 반복하지 않는다. 대신 지정된 작업이 권장 로드맵의 어느 위치인지 간단히 알리고 해당 요청을 우선한다.

## 사용자 답변에 따른 진행

### 권장 로드맵을 선택한 경우

1. `auth`의 전체 API와 테스트 현황을 조사한다.
2. 구현을 바로 바꾸기 전에 누락·불일치와 검증 계획을 보고한다.
3. 사용자가 범위를 확인하면 API 단위로 구현과 검증을 진행한다.
4. 도메인 완료 체크리스트를 모두 충족한 뒤 다음 도메인으로 넘어간다.

세부 순서는 [다음 개발 권장 순서](development-standards/next-steps.md)를 따른다.

### 다른 작업을 선택한 경우

1. 사용자가 지정한 작업을 최우선으로 처리한다.
2. 관련 개발 표준과 아키텍처 문서를 먼저 확인한다.
3. 기존 API 계약과 작업 트리를 보존하면서 필요한 범위만 변경한다.
4. 완료 후 권장 로드맵의 현재 위치와 다음 후보를 알려준다.

## 세션 종료 전 인수인계

작업을 끝내거나 다음 세션으로 넘길 때 다음 내용을 남긴다.

- 완료한 기능과 변경 파일
- 실행한 테스트와 실제 결과
- 확인하지 못한 외부 API·DB·운영 환경 범위
- 남은 문제와 재현 방법
- 다음에 진행할 권장 작업
- 코드와 함께 갱신한 문서

진행 중인 임시 상태를 영구 개발 표준 문서에 기록하지 않는다. 단기 작업 상태는 이슈·PR 설명 또는 별도 작업 기록에 남기고, 확정된 구조와 규칙만 개발 표준에 반영한다.

## 인수인계 원칙

- 사용자의 명시적인 요청이 권장 로드맵보다 우선한다.
- 이전 세션의 성공 보고만으로 현재 코드가 정상이라고 단정하지 않는다.
- 자동 테스트 통과와 실제 PostgreSQL·외부 API 실호출 검증을 구분해 보고한다.
- 관련 없는 기존 변경, 비밀 설정과 운영 데이터를 건드리지 않는다.
- 구현, 테스트와 문서가 함께 현재 상태를 설명하도록 유지한다.

## 현재 로컬 검증 상태 (2026-09-02, `dev` 기준)

전체 H2 자동화 테스트와 별개로, 격리한 Docker PostgreSQL 16에 Flyway V1~V16을 적용하고 Docker 서버에서 `/api/v1` HTTP 스모크 45건을 다시 실행했다.

### 되는 것

- PostgreSQL 접속, Flyway V16, 시드·로컬 더미 데이터 조회
- 인증, 프로필, 친구, 설문, 여행 생성·참여·초대, 날짜 조율, 일정, 경비·공금·정산, 찜, 대시보드, 공지, 약관, 문의
- 일정 자동 생성 후 `ITINERARY` 경로 추천·선택
- 초대 참여 시 출발·귀가 장소를 넣은 멤버의 `DEPARTURE`/`RETURN` 경로 추천
- 여행 시작, 현장 상황 등록, 변경안 조회
- 혼잡·TourAPI·기상청 조회는 이 로컬 환경에서 200

### 안 되거나 빠진 것

1. **소유자 출발·귀가 장소 API가 없다.**
   `POST /api/v1/trips`는 `SEPARATE` 모드로 만들고 소유자 `departurePlaceId`/`returnPlaceId`를 넣지 않는다. 이후 `PUT /api/v1/trips/{tripId}/participants/{본인}`은 `409 TRIP_ALREADY_JOINED`다. 그 결과 소유자 `DEPARTURE` 경로 추천은 `400 ROUTE_DEPARTURE_PLACE_REQUIRED`다. 새 멤버만 `POST /api/v1/trip-memberships`의 장소 필드로 넣을 수 있다.

2. **AI 추천·여행 상황 대처는 비활성이다.**
   `APP_AI_ENABLED=false`이면
   `POST /api/v1/recommendations/places` → `503 RECOMMENDATION_UNAVAILABLE`
   `POST /api/v1/recommendations/situations` → `503 SITUATION_AGENT_UNAVAILABLE`
   `POST /api/v1/trips/{tripId}/situation-responses` → 503
   핵심 여행 API를 막지는 않는다.

3. **기본 실행은 H2다.** `application.yml` datasource 기본값이 H2 메모리라서, 인자 없이 `bootRun`하면 PostgreSQL이 아니라 H2로 뜬다. 로컬 PostgreSQL로 검증하려면 datasource URL·계정·드라이버를 명시해야 한다. `docker-compose-db.yaml`의 Redis는 앱이 사용하지 않는다.

4. **자동 일정 응답 필드명이 다른 API와 다르다.**
   `GET/POST /api/v1/trips/{tripId}/plans`의 `PlanResponse`는 `trip_id`, `plan_date`, `created_at`처럼 snake_case다. 일정·여행 등 나머지 공개 DTO는 camelCase다.

5. **아직 이 세션에서 확인하지 않은 것.** TMAP 운영 키 실호출, 탈퇴 API, Refresh Token, PostgreSQL Testcontainers CI, 운영 서버 배포 검증.

### Google 로그인 (2026-09-02)

- API: `POST /api/v1/auth/google-tokens` `{ "idToken": "..." }`
- 서버는 Google 공식 Java 검증기로 ID 토큰의 서명·issuer·audience·만료를 확인한 뒤 기존 JWT를 발급한다. `social_login_accounts`에 `GOOGLE` subject를 저장하고, 같은 검증 이메일의 기존 계정이 있으면 연결한다.
- `.env.example`에는 `GOOGLE_CLIENT_ID`, `GOOGLE_ANDROID_CLIENT_ID` placeholder만 있다. 실값은 `.env`를 열지 않았으므로 콘솔의 **웹 클라이언트 ID**를 `GOOGLE_CLIENT_ID`에 넣었는지 직접 확인해야 한다.
- 키가 비어 있으면 `503 AUTH_GOOGLE_NOT_CONFIGURED`가 정상이다. client secret은 Android ID 토큰 검증에 필요 없다.
