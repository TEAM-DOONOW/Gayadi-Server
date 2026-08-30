# Gayadi-Server 인수인계

이 문서가 새 작업자의 시작점이다. 먼저 `AGENTS.md`, 이 문서, `application.yml`, 최신 Flyway
마이그레이션 순서로 읽는다. 현재 작업 범위는 **서버뿐이며 Android 저장소는 수정하지 않는다.**

## 빠른 실행

- 기술: Java 21, Spring Boot 4.1, Gradle Wrapper, PostgreSQL, Flyway
- 기본 실행: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정하고 `gradlew.bat bootRun`
- 운영: `SPRING_PROFILES_ACTIVE=prod`; DB 세 값과 32자 이상의 `JWT_SECRET`이 필수
- 검증: `gradlew.bat clean test bootJar`
- Swagger UI: `/api/docs`, OpenAPI JSON: `/api/openapi`
- 로컬 5432에 PostgreSQL이 없으면 기본 실행은 실패하는 것이 정상이다. H2 자동 대체는 없다.

## 데이터 저장 원칙

- PostgreSQL이 개발·운영의 유일한 업무 데이터 저장소다.
- H2는 자동화 테스트에서만 PostgreSQL 호환 모드로 사용한다.
- Firebase, Firestore, FCM 및 서버 로컬 파일을 업무 데이터 저장소로 사용하지 않는다.
- 스키마는 Flyway 이력으로 관리하고 Hibernate는 `ddl-auto=validate`로 매핑만 검증한다.

## 영속성 결정

PostgreSQL 연결에는 JPA와 Spring JDBC가 같은 `DataSource`와 Spring 트랜잭션 관리자를 사용한다.
`JdbcClient`라고 해서 로컬 저장이나 우회 DB가 아니며 PostgreSQL에 직접 연결된다.

| 영역 | 현재 방식 | 선택 이유 |
| --- | --- | --- |
| 사용자 계정·로그인 쓰기 | Spring Data JPA | 단일 aggregate, 잠금과 수명주기 콜백이 명확함 |
| 사용자 탈퇴 정리 | JdbcClient | 여러 도메인 FK를 한 트랜잭션에서 일괄 정리 |
| 여행·참여자·초대 | JdbcClient | 상태 전이, 잠금, 다중 테이블 갱신이 기존 SQL로 검증됨 |
| 설문·일정·경로·이벤트 | JdbcClient | JSON/조인/일괄 변경과 조회 투영이 많음 |
| 경비·날짜 조율·친구·찜 | JdbcClient | 집계와 upsert/다중 행 처리가 중심 |
| 공지·약관·장소 조회 | JdbcClient 읽기 | DTO 투영 조회이며 엔티티 변경 추적이 불필요함 |

전 도메인을 형식적으로 JPA로 바꾸지 않는다. 이전이 필요하면 한 도메인의 엔티티, Repository,
서비스, 마이그레이션, 통합 테스트를 함께 바꾸고 기존 SQL과 결과가 같은지 검증한다. 한 쓰기
유스케이스에서 JPA와 JDBC를 섞어 flush 순서에 의존하는 코드를 새로 만들지 않는다.

## 코드 구조

- 각 최상위 패키지(`travel`, `survey`, `schedule`, `route`, `expense` 등)가 도메인 경계다.
- Controller: HTTP/Swagger/Validation/인증 principal 변환만 담당한다.
- Service: 트랜잭션과 도메인 규칙을 담당한다.
- `persistence` 하위 패키지: JPA 엔티티와 Repository를 둔다.
- 외부 API adapter: `tourapi`, `weather`, `congestion`, `route`에 위치한다.
- 외부 API 키·URL·timeout·캐시 운영값은 각 도메인의 `@ConfigurationProperties`가 소유한다.
- API에서는 영속 엔티티를 직접 반환하지 않는다. 공개 성공 응답은
  `common.dto.ApiResponses`의 typed response와 `ApiResponseMapper`를 통해 반환한다.
  JDBC 조회 `Map<String,Object>`는 서비스 내부 투영이며 HTTP 응답 타입으로 노출하지 않는다.
- 현재 JPA 영속 엔티티는 `auth.persistence.UserAccount`다. 나머지 JDBC 중심 도메인에는
  변경 추적용 JPA 엔티티를 중복 정의하지 않는다. JPA로 옮길 때는 도메인 단위로 Entity,
  Repository, mapper, 트랜잭션 테스트를 함께 이전한다.

## 현재 구현

- Flyway V1~V16: 초기 ERD의 21개 핵심 테이블과 서버 확장 11개 테이블
- Spring Data JPA/Hibernate: 사용자 계정·로그인 쓰기 경로부터 적용
- JdbcClient: 기존 여행·설문·일정·경로·이벤트 등 나머지 도메인의 점진적 이전 대상
- 인증: 이메일/비밀번호와 서버 발급 HMAC JWT
- 여행, 참여자, 초대, 설문, 일정, 경로, 경비, 날짜 조율, 친구, 찜, 공지, 문의, 약관 API
- TourAPI·기상청·관광지 집중률 API 및 자료 부재 시 달력 기반 혼잡 추정
- Groq 호환 Chat 모델과 TourAPI를 조합하는 API-first 추천 Agent
- Swagger `/api/docs`, OpenAPI `/api/openapi`

### 외부 연동 활성 조건

- 관광/혼잡/날씨: 키가 없으면 해당 직접 호출은 명시적 오류 또는 문서화된 혼잡 추정으로 처리한다.
- 추천 Agent: `APP_AI_ENABLED=true`, `AI_CHAT_MODEL=openai`, Groq 호환 URL·모델·키가 필요하다.
- TMAP: `ROUTE_PROVIDER=tmap`과 `SKT_APPKEY`가 필요하다. 실패 시 로컬 예상 경로 사용 여부는
  `TMAP_FALLBACK_TO_LOCAL`로 결정한다.
- 임베딩은 Agent 활성 여부와 무관하게 구현돼 있지 않다.

## 미구현 — 임의로 추가하지 말 것

- 임베딩 모델, VectorStore, PGvector 및 임베딩 생성/관리 API
- Firebase, Firestore, FCM
- OAuth/OIDC 공급자 로그인과 토큰 검증
- Redis 캐시와 분산 락
- SSE/WebSocket 실시간 알림 전송
- TMAP 운영 키를 사용한 실제 대중교통 운영 검증
- PostgreSQL Testcontainers 기반 CI 통합 테스트
- Android 코드 변경 및 Android 저장소 교체

미구현 기능은 주석 처리된 코드, 가짜 응답, Fake/로컬 저장소, 비활성 의존성으로 미리 넣지 않는다.
구현 요청이 생기면 이 문서의 항목을 작업 범위와 검증 기준으로 먼저 갱신한 뒤 도메인 단위로 완성한다.

`V3__add_vector_store.sql`은 과거에 배포된 **DDL 없는 주석 전용 Flyway 이력**이다. 현재 임베딩이
구현됐다는 의미가 아니며, 적용된 Flyway 파일의 체크섬을 깨뜨리지 않기 위해 파일명과 내용만 보존한다.
VectorStore 의존성·빈·테이블은 현재 런타임과 스키마에 존재하지 않는다.

## 현재 리팩터링 상태

- Firebase 서버 의존성·설정·서비스 제거
- 기본 실행 DB를 H2에서 PostgreSQL로 변경
- 테스트 태스크만 H2를 주입하도록 분리
- 사용자 인증 쓰기를 Spring Data JPA 엔티티·Repository로 이전
- 로그인 실패 횟수와 잠금 시간을 타입 안전 설정으로 이전
- 운영 프로필에서 PostgreSQL 필수 값, Flyway 검증, Hibernate 검증, Hikari 풀 설정을 강제
- 초기 ERD와 현재 확장 스키마를 검사하는 통합 테스트 추가
- TourAPI·혼잡·기상청·TMAP 설정을 타입 안전 `@ConfigurationProperties`로 이동
- 서비스 내부의 수동 HTTP adapter/대체 RouteProvider 생성을 Spring 빈 주입으로 변경
- 사용되지 않던 `ADMIN_USER_ID` 설정과 Spring 내부 패키지 호환 스텁 제거

## 검증 기준점

- 2026-08-30 `gradlew.bat clean test bootJar` 성공
- 자동화 테스트 86개 통과: Spring context, Swagger 응답 계약, ERD 테이블·제약조건,
  Agent 사용자 여정과 외부 API fallback 포함
- 공개 Controller 성공 응답에 `Map<String,Object>`가 다시 노출되면 계약 테스트가 실패한다.
- 의존성 잠금·검증 메타데이터를 갱신했고 확인 당시 알려진 OSV 취약점은 없었다.
- 로컬에 PostgreSQL 인스턴스가 없어 실제 PostgreSQL 접속·V1~V16 migration smoke test는
  아직 수행하지 않았다. H2 호환 테스트 통과를 운영 PostgreSQL 검증 완료로 간주하지 않는다.

## DB 스키마 기준

- V1: 사용자가 제공한 초기 ERD의 핵심 21개 테이블
- V2~V15: 기준 데이터, 인증, 친구, 조회 인덱스와 Android 계약 보강
- V16: 날짜 조율, 경비, 공금, 공지, 문의 확장 7개 테이블
- 현재 계약 테스트가 확인하는 총 업무 테이블은 32개다.
- 적용된 마이그레이션 파일을 수정하지 않는다. 변경은 다음 버전 파일로만 추가한다.

## 알려진 부채와 다음 순서

1. PostgreSQL Testcontainers로 실제 PostgreSQL Flyway/JPA/JDBC 통합 테스트 추가
2. 응답 DTO 계약을 유지하면서 서비스 내부 JDBC 투영도 도메인별 projection/row mapper로 점진 전환
3. 사용자 탈퇴처럼 도메인 횡단 정리가 큰 유스케이스를 전용 application service로 분리
4. 운영 PostgreSQL에서 V1~V16 적용과 주요 API smoke test 수행
5. 실제 키로 TMAP과 Agent 외부 호출 검증

현재 자동화 테스트는 H2 PostgreSQL 호환 모드이므로 PostgreSQL 고유 동작을 100% 보장하지 않는다.
운영 배포 완료라고 판단하려면 1번과 4번이 필요하다.

## 변경 금지/주의

- Android 코드는 명시적 승인 전 수정하지 않는다.
- Firebase/Firestore/로컬 파일 저장을 다시 추가하지 않는다.
- 미구현 API를 빈 컨트롤러, fake 응답 또는 비활성 의존성으로 미리 만들지 않는다.
- 외부 API 키와 `.env` 실값을 로그·테스트 fixture·문서에 넣지 않는다.
