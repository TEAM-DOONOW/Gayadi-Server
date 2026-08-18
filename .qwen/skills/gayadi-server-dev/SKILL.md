---
name: gayadi-server-dev
description: Gayadi-Server 개발 워크플로우. 새 API 추가, 모듈 확장, Spring AI 연동, 마이그레이션 작성 시 사용.
---

# Gayadi-Server 개발 스킬

## 새 API 엔드포인트 추가

1. 해당 모듈 패키지에 Controller 메서드 추가
   - `@RestController` + `@RequestMapping("/api/v1/...")`
   - 요청 DTO는 data class + `@field:NotBlank` 등 검증
   - 응답은 `Map<String, Any>` 또는 data class
2. Service에 비즈니스 로직 구현
   - `JdbcClient`로 SQL 실행
   - 오류: `throw ApiException(HttpStatus.XXX, "한국어 메시지")`
   - 트랜잭션: `@Transactional`
3. 스키마 변경 필요 시 `src/main/resources/db/migration/V{N}__{설명}.sql`
4. 테스트: `src/test/kotlin/com/gayadi/server/`에 통합 테스트 추가
   - `@SpringBootTest` + `@Autowired` 서비스 직접 호출
   - 또는 `HttpClient`로 HTTP 스모크 테스트

## 새 모듈 추가

1. `src/main/kotlin/com/gayadi/server/{module}/` 패키지 생성
2. `{Module}Controller.kt` + `{Module}Service.kt` 기본 구조
3. SKILLS.md, QWEN.md 모듈 목록 업데이트
4. 기존 모듈 의존은 Service 주입으로 (순환 의존 금지)

## Spring AI 작업 시

- AI 빈은 `AiConfig.kt`에서 조건부 생성 (`OPENAI_API_KEY` 필요)
- `@ConditionalOnBean(ChatClient::class)` 가드 유지
- VectorStore: 로컬 SimpleVectorStore, 운영 PgVector
- Embedding 모델: `text-embedding-3-small` (1536차원)
- Chat 모델: `gpt-4o-mini`
- 새 AI 기능 추가 시 기존 recommendation 모듈 패턴 참고

## 마이그레이션 작성 시

- 파일명: `V{N}__{snake_case_설명}.sql`
- H2(PostgreSQL 모드) + PostgreSQL 둘 다 호환되어야 함
- `CREATE EXTENSION` 등 H2 미지원 구문은 별도 프로파일로 분리
- 기존 V1(스키마), V2(시드), V3(벡터스토어 placeholder) 이후 번호 사용

## 검증 체크리스트

- [ ] `./gradlew test` 통과 (Windows: `.\gradlew.bat test`)
- [ ] 새 엔드포인트가 기존 API 계약 안 깨는지 확인
- [ ] `OPENAI_API_KEY` 없이도 앱 정상 기동되는지 확인
- [ ] Flyway 마이그레이션이 H2 + PostgreSQL 둘 다 동작하는지 확인
- [ ] 커밋 메시지: `<type>/#<issue>: <subject>` 형식
