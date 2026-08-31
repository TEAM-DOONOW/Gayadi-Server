# DTO 작성 기준

DTO는 외부 API 계약을 표현하며 DB Row나 내부 도메인 객체를 그대로 노출하지 않는다.

## 종류

```text
dto/
├─ request/   # 클라이언트 입력
└─ response/  # 클라이언트 출력
```

DTO는 다른 계층의 클래스와 섞이지 않도록 도메인의 `dto/request`, `dto/response`에 둔다.

## 이름 규칙

| 용도 | 예시 |
|---|---|
| 저장 요청 | `FavoritePlaceSaveRequest` |
| 생성 요청 | `ExpenseCreateRequest` |
| 수정 요청 | `ExpenseUpdateRequest` |
| 상세 응답 | `ExpenseResponse` |
| 목록 항목 | `ExpenseSummaryResponse` |
| 내부 조회 결과 | `ExpenseQueryResult` (`query` 패키지) |

Create와 Update의 필수 필드가 다르면 DTO를 분리한다. 구조가 완전히 같을 때만 하나의 Request DTO를 공유한다.

## Request DTO

Request DTO에는 입력 형식 검증만 둔다.

```java
public record FavoritePlaceRequest(
        @Schema(
                description = "사용자가 남긴 메모",
                example = "오전 관람 예정")
        @Size(max = 500, message = "{validation.favorite.memo.size}")
        String memo
) {
}
```

DTO에서 처리할 항목:

- `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`
- 문자열·숫자 범위와 기본 형식
- 중첩 요청의 `@Valid`

Validation 어노테이션과 OpenAPI 어노테이션의 역할은 다르다. `@Size`는 런타임 입력 검증이고 `@Schema`는 API 문서를 보완한다. Springdoc이 필드명, Java 타입과 Validation 정보로 기본 스키마를 생성하므로 모든 Request 필드에 `@Schema`를 반복할 필요는 없다.

Service에서 처리할 항목:

- 사용자와 여행의 존재 여부
- 접근 권한
- 여행 상태 전이
- 다른 데이터와 비교해야 하는 날짜 범위
- 중복과 동시성 충돌

## Response DTO

Response DTO는 JSON 필드와 타입을 명확하게 고정한다.

```java
public record FavoritePlaceResponse(
        long id,

        String name,

        @Schema(
                description = "장소 카테고리",
                example = "ATTRACTION")
        PlaceCategory category,

        String memo,

        @Schema(
                description = "찜 목록에 저장된 시각",
                example = "2026-08-31T10:30:00")
        LocalDateTime favoritedAt
) {
}
```

- `Map<String, Object>`를 공개 응답으로 사용하지 않는다.
- DB Entity나 Row 객체를 그대로 반환하지 않는다.
- 내부 필드와 민감정보를 포함하지 않는다.
- nullable 필드는 타입과 API 문서에서 명확히 표시한다.
- Response의 `@Schema`는 설명, 예시, 필수 여부와 nullable 여부를 중심으로 간결하게 작성한다.
- 고정된 허용값은 `allowableValues`를 반복하지 않고 도메인 enum 타입으로 표현한다.
- 기존 JSON 필드명과 null 처리 방식은 계약 테스트로 보호한다.
- 전환이 끝난 도메인의 OpenAPI는 Controller가 실제 Response DTO를 직접 참조한다.
- 아직 전환하지 않은 복합 API의 중앙 스키마는 해당 도메인 전환 시 제거한다.

## OpenAPI 어노테이션 적용 기준

`@Schema`, `description`, `example`은 실행, Validation과 JSON 직렬화에 필요하지 않다. Springdoc이 기본 스키마를 자동 생성하므로 문서 이해에 실제 도움이 되는 곳에만 추가한다.

| 항목 | 사용하는 경우 |
|---|---|
| `description` | 필드명만으로 업무 의미를 이해하기 어렵거나 단위·기준 설명이 필요한 경우 |
| `example` | 날짜, 시각, URL, cursor, enum 직렬화 값처럼 정확한 입력 형식을 보여줄 필요가 있는 경우 |
| `requiredMode` | 자동 추론 결과가 실제 API 계약과 다르거나 필수 여부를 명시적으로 고정해야 하는 경우 |
| `nullable` | null 허용 여부가 자동 생성 문서에 정확히 나타나지 않아 보완이 필요한 경우 |

- 자명한 `id`, `name` 등에 설명과 예시를 기계적으로 반복하지 않는다.
- 클래스·record 단위 `@Schema(name, description)`도 이름 충돌을 피하거나 DTO 목적을 설명할 때 사용한다.
- `requiredMode = REQUIRED`와 `nullable = true`를 동시에 사용하지 않는다.
- 예시를 작성하면 실제 JSON의 대소문자와 날짜·시각 형식을 사용한다.
- 중첩 객체와 목록은 각 필드에 큰 JSON 예시를 반복하기보다 Controller의 요청·응답 예시나 OpenAPI Example Object로 제공한다.
- 어노테이션을 사용한 경우 어노테이션과 변수는 같은 줄에 쓰지 않고 필드 사이에 빈 줄을 둔다.

```java
@Schema(
        description = "여행 시작일",
        example = "2026-09-01",
        requiredMode = Schema.RequiredMode.REQUIRED)
@NotBlank(message = "{validation.trip.start-date.required}")
String startDate
```

`@Schema`가 없는 DTO는 누락으로 판단하지 않는다. 대신 Swagger UI 또는 `/v3/api-docs`에서 타입, required, nullable과 enum 표현이 실제 API 계약과 다른지를 검토하고 필요한 부분만 명시적으로 보완한다.

## 변환 위치

단순 변환은 Service의 private 메서드로 시작할 수 있다. 변환이 반복되거나 복잡해지면 별도 Mapper로 분리한다. Repository의 Query Result와 외부 Response DTO는 역할이 다르므로 별도 타입으로 유지한다.

```text
Model 또는 Query Result → Mapper → Response DTO
```

Repository가 Controller용 Response DTO를 직접 반환하는 구조는 단순 조회 전용 Repository를 제외하고 사용하지 않는다.

## Validation 메시지

Validation 문구는 도메인별 i18n 파일에서 관리한다.

```properties
validation.favorite.memo.size=메모는 최대 {max}자까지 입력할 수 있습니다.
```

한국어와 영어 파일에 같은 키를 추가하고 DTO 어노테이션에서는 메시지 키만 참조한다.
