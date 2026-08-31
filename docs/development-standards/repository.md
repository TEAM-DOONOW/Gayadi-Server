# Repository 작성 기준

Repository는 Service에 섞여 있는 SQL과 DB Row 매핑을 담당한다.

## 기본 형태

현재 저장 기술은 `JdbcClient`이므로 구현체 하나를 기본으로 사용한다.

```java
@Repository
public class FavoritePlaceRepository {

    private final JdbcClient jdbc;

    public Optional<FavoritePlace> find(long userId, long placeId) {
        // SQL 실행과 Row 매핑
    }
}
```

저장소 교체나 여러 구현체가 실제로 필요할 때만 인터페이스와 구현체를 분리한다.

```text
FavoritePlaceRepository
JdbcFavoritePlaceRepository
```

모든 Repository에 형식적인 인터페이스를 만들거나 공통 CRUD BaseRepository를 두지 않는다.

## 메서드 규칙

- 단건 조회: `Optional<T>`
- 목록 조회: `List<T>`이며 `null`을 반환하지 않는다.
- 존재 여부: `boolean exists...`
- 변경 쿼리: 변경 건수 또는 의미 있는 결과 반환
- 저장 후 재조회가 필요하면 Repository 내부 메서드로 캡슐화
- 사용자에게 보여줄 예외 문구는 Repository에서 만들지 않는다.

## Row 매핑

`Map<String, Object>`는 Repository 내부까지만 허용하고 외부로 노출하지 않는다.

```text
DB Row Map → Repository 매핑 → Model 또는 Query Result
```

반복되는 컬럼 변환은 명시적인 mapper 메서드나 `RowMapper`로 분리한다. DB의 snake_case 컬럼명을 Service가 알지 못하게 한다.

## 트랜잭션과 잠금

- `@Transactional`은 여러 저장 작업을 하나의 유스케이스로 묶는 Service에 둔다.
- Repository는 `FOR UPDATE` 같은 잠금 쿼리를 제공할 수 있다.
- 잠금 대상과 순서는 Service가 유스케이스 기준으로 결정한다.
- Unique Constraint, Foreign Key 같은 DB 제약조건은 최종 방어선으로 유지한다.

```java
@Transactional
public FavoritePlaceResponse save(long userId, long placeId, String memo) {
    userRepository.lockById(userId);
    favoritePlaceRepository.upsert(userId, placeId, memo);
    return mapper.toResponse(favoritePlaceRepository.get(userId, placeId));
}
```

## 조회 결과

Repository는 웹 계층의 Response DTO에 직접 의존하지 않고 도메인의 `query` 결과를 반환한다. 현재 필드가 같더라도 DB 조회 계약과 API 계약을 분리해 각각 변경할 수 있게 한다.

```text
FavoritePlaceQueryResult
DashboardSummary
TripMemberView
```

Query Result는 DB Row Map보다 타입이 명확해야 하며 `query` 패키지에 둔다. Entity와 Value Object 같은 업무 모델은 `model` 패키지에 둔다.

DB에 문자열로 저장된 고정 상태·분류 값은 Row 매핑 시점에 도메인 enum으로 변환한다. Query Result와 Response DTO가 같은 enum을 사용하게 하며, Service에서 문자열을 다시 해석하지 않는다.

## PostgreSQL 검증

Repository 전환 후 다음 항목을 통합 테스트한다.

- Flyway 마이그레이션 실행
- `FOR UPDATE` 잠금
- Unique Constraint 충돌
- JSON, 날짜·시간, Boolean 타입
- H2와 PostgreSQL의 SQL 동작 차이
