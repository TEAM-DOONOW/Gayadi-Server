# 패키지와 계층 구조

## 기본 구조

현재 프로젝트는 도메인 중심 패키지를 사용한다. Controller, Service, Repository와 ErrorCode가 각각 하나이거나 소수라면 도메인 루트에 두고 역할이 분명한 데이터 타입만 하위 패키지로 구분한다.

```text
notice/
├─ NoticeController.java
├─ NoticeService.java
├─ NoticeRepository.java
├─ NoticeErrorCode.java
├─ dto/response/
│  └─ NoticeResponse.java
└─ query/
   └─ NoticeQueryResult.java
```

도메인이 커져 파일 탐색이 어려워지거나 `admin`, `internal`, `public`처럼 기능과 접근 경계가 분명해질 때만 기능 단위 하위 패키지를 도입한다. Controller·Service·Repository 종류별 폴더를 기계적으로 만들지 않는다.

## 한 도메인의 여러 Controller

Controller 개수는 도메인 분리 기준이 아니다. 같은 업무 모델과 Service를 사용하더라도 URL 자원, 인증 경계 또는 사용 주체가 다르면 Controller를 나누는 것이 자연스럽다.

- `auth/AuthController`, `auth/UserController`: 토큰 발급과 계정 관리를 분리하므로 현재 배치를 유지한다. 사용자 프로필 기능이 커지면 `user` 도메인 독립을 검토한다.
- `friendship/FriendshipController`, `friendship/UserSearchController`: 사용자 검색이 친구 추가 유스케이스에 한정되므로 `friendship`에 함께 둔다.
- `schedule/PlanController`, `schedule/ScheduleItemController`: 계획 생성과 일정 항목 편집은 같은 일정 도메인의 서로 다른 API 자원이므로 함께 둔다.
- `recommendation/RecommendationController`, `recommendation/TripSituationController`: 추천과 상황 대처가 같은 AI 추천 구성 요소를 공유하므로 현재는 함께 둔다.
- `recommendation/EmbeddingAdminController`: 일반 사용자 추천 API와 인증 주체가 다른 운영 API이므로 파일이 더 늘어나면 `recommendation/admin` 하위 패키지로 가장 먼저 분리한다.

Controller가 두세 개라는 이유만으로 `controller` 폴더를 만들지는 않는다. 다음 중 하나가 성립할 때 역할별 하위 패키지를 도입한다.

- Controller·Service·Repository가 각각 여러 개라 도메인 루트 탐색이 어려운 경우
- `admin`, `internal`, `public`처럼 접근 경계가 명확히 다른 경우
- 하나의 도메인 안에서 독립적인 하위 기능 묶음이 지속해서 확장되는 경우

하위 패키지를 도입하면 Controller만 따로 모으기보다 `recommendation/admin`처럼 기능 단위로 Controller와 관련 DTO·Service를 함께 배치한다.

## 프로젝트 기본 배치

```text
domain/
├─ DomainController.java
├─ DomainService.java
├─ DomainRepository.java
├─ DomainErrorCode.java
├─ command/
├─ dto/
│  ├─ request/
│  └─ response/
├─ query/
└─ model/
```

- `dto/request`: API 요청 단위 타입과 입력 Validation
- `dto/response`: 외부에 공개되는 JSON 응답과 OpenAPI 스키마
- `command`: Controller나 다른 도메인이 Service 유스케이스에 전달하는 내부 명령
- `query`: Repository가 DB Row를 매핑하는 내부 `*QueryResult`
- `model`: 여러 계층이 함께 사용하는 상태, 분류, 값 객체와 enum
- `Repository`: SQL과 저장 형식 변환을 담당하며 파일이 하나라면 별도 repository 폴더를 만들지 않는다.

Query Result는 Entity도 API DTO도 아니다. Repository와 Service 사이의 내부 조회 모델이며 Service가 이를 Response DTO로 변환한다. JPA를 도입하는 도메인은 영속 Entity를 별도로 두되 Query Result와 Response DTO를 그대로 Entity로 대체하지 않는다.

Command도 API DTO와 구분한다. HTTP Validation과 JSON 계약은 Request DTO가 담당하고, Controller는 검증된 값을 내부 Command로 변환해 Service에 전달한다. 단순 DTO 전달만으로 충분한 유스케이스에는 형식적인 Command를 만들지 않는다.

## 계층별 책임

| 계층 | 담당 | 담당하지 않는 것 |
|---|---|---|
| Controller | HTTP 입력, 인증 사용자, `@Valid`, 상태 코드 | SQL, 업무 규칙, 응답 Map 조립 |
| DTO | API 입력·출력 필드, 형식 검증 | 권한 검사, DB 조회, 상태 전이 |
| Service | 유스케이스, 권한, 업무 규칙, 트랜잭션 | SQL, DB 컬럼명, HTTP 객체 |
| Repository | SQL 실행, Row 매핑, 저장과 조회 | HTTP 응답, 사용자용 메시지, 유스케이스 조합 |
| Model | 업무 상태와 값 | API 전용 필드, DB 구현 세부사항 |

세부 코드 배치와 줄바꿈 규칙은 [Java 코드 작성 형식](code-style.md)을 따른다.

## 호출 방향

```text
Controller → Service → Repository → Database
                 ↓
               Model
```

- Controller는 Repository를 직접 호출하지 않는다.
- Repository는 Service나 Controller를 호출하지 않는다.
- 다른 도메인의 데이터를 변경할 때는 해당 도메인의 Service를 우선 사용한다.
- 여러 도메인을 조합하는 읽기 화면은 `DashboardQueryRepository` 같은 조회 전용 Repository를 둘 수 있다.

## REST의 View

이 프로젝트는 서버 렌더링 화면이 없는 REST API다. 전통적인 MVC의 View 역할은 JSON으로 직렬화되는 Response DTO가 담당한다.
