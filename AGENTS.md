# Gayadi-Server 작업 지침

작업을 시작하기 전에 `HANDOFF.md`의 구현 현황과 미구현 범위를 확인한다.
미구현 항목을 사용자 요청 없이 의존성, 빈 API, 임시 저장소 또는 스텁으로 추가하지 않는다.

## 데이터 아키텍처

- PostgreSQL이 모든 업무 데이터의 유일한 영속 저장소이자 원본이다.
- Firebase, Firestore와 단말 로컬 저장소를 업무 데이터 저장소로 사용하지 않는다.
- 로컬 개발과 테스트에서만 H2를 허용한다. 운영 프로필은 PostgreSQL 드라이버와 필수
  `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 설정으로 실행한다.
- 업무 스키마 변경은 기존 파일 수정이 아니라 새 Flyway `V{N}__{description}.sql`로만 수행한다.
- 영속성 기술은 도메인 단위로 일관되게 선택한다. 단순 aggregate와 엔티티 수명주기 중심 기능은
  Spring Data JPA를 우선하고, 다중 조인·일괄 갱신·PostgreSQL SQL 제어가 핵심인 기존 도메인은
  `JdbcClient`를 유지할 수 있다. 같은 쓰기 유스케이스 안에서 JPA와 JDBC를 임의로 섞지 않는다.
- JDBC 쓰기도 반드시 Spring 트랜잭션과 바인딩 파라미터를 사용한다. 문자열로 사용자 값을 SQL에
  연결하지 않으며, 생성 키는 `KeyHelper`를 통해 처리한다.
- 영속 엔티티와 API DTO를 분리한다. 컨트롤러는 엔티티를 직접 반환하지 않는다.
- 공개 성공 응답은 구체적인 DTO 타입으로 선언한다. JDBC 조회 결과인 `Map<String,Object>`는
  서비스 내부 투영으로만 사용하고 HTTP 응답 타입으로 노출하지 않는다.
- 한 엔드포인트에서만 쓰는 요청 DTO는 해당 컨트롤러에 둘 수 있지만, 여러 도메인에서 공유하거나
  재사용하는 계약은 별도 `dto` 패키지로 분리한다.
- 테이블·제약조건·인덱스는 PostgreSQL 기준으로 설계하고 H2 호환 테스트도 유지한다.

## API 및 Android 연동

- Android 업무 데이터는 `/api/v1/**` API를 통해서만 읽고 쓴다.
- Android에 로컬/Fake/Firebase 우회 저장을 요구하지 않는다. 필요한 서버 API를 먼저 구현한다.
- 기존 Android 모델을 수용할 수 있으면 서버 DTO와 mapper에서 맞추고 화면 변경을 최소화한다.
- 모든 공개 API를 Swagger에 문서화하고 인증 필요 여부, 요청·응답, 오류를 명시한다.
- 인증 주체는 bearer token에서 얻은 사용자 ID를 사용하며 요청 본문의 사용자 ID를 신뢰하지 않는다.

## 구현 품질

- 도메인 패키지 구조를 유지하고 비즈니스 로직을 컨트롤러에 두지 않는다.
- 외부 API 설정은 도메인별 `@ConfigurationProperties`로 바인딩한다. 서비스에서 `@Value`, URL,
  timeout, 운영용 캐시 크기를 직접 하드코딩하거나 Spring 빈을 `new`로 생성하지 않는다.
- 비즈니스 오류는 `ApiException`과 한국어 메시지로 반환한다.
- 외부 API와 AI 기능이 비활성화돼도 PostgreSQL 기반 핵심 API는 정상 동작해야 한다.
- `HANDOFF.md`에서 미구현으로 표시한 기능은 실제 코드·설정·Swagger에서 구현된 것처럼 노출하지 않는다.
- 변경 후 단위·통합 테스트, Flyway 마이그레이션 검증, 애플리케이션 조립을 실행한다.
- 비밀번호, 토큰, 서비스 계정 키와 `.env` 값은 읽거나 로그·문서·커밋에 노출하지 않는다.

## 빌드와 Git

- Windows에서는 `gradlew.bat clean test bootJar`로 검증한다.
- 커밋은 `.codex/skills/git-commit-assistant/`의 규약을 따른다.
- 관련 없는 사용자 변경과 비밀 파일을 스테이징하지 않는다.
