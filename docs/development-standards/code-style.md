# Java 코드 작성 형식

새 DTO, Query Result, enum과 Repository를 작성하거나 기존 파일을 수정할 때 아래 형식을 유지한다.

## DTO와 record

- record 컴포넌트는 한 줄에 하나만 선언한다.
- 컴포넌트 어노테이션과 변수 선언은 같은 줄에 쓰지 않는다.
- 컴포넌트 어노테이션이 있는 record는 필드 단위를 구분하기 위해 컴포넌트 사이에 빈 줄을 한 줄 둔다.
- 컴포넌트 어노테이션이 전혀 없는 record는 불필요한 빈 줄 없이 컴포넌트를 연속해서 배치한다.
- Request DTO에만 Bean Validation을 적용한다.
- API Request·Response DTO에는 타입 단위 `@Schema(name, description)`를 작성한다.
- `@Schema` 필드는 자동 생성 문서로 의미나 형식을 충분히 설명할 수 없는 경우에 사용한다.
- `description`은 업무 의미·단위 설명이 필요할 때, `example`은 형식 안내가 필요할 때 사용한다.
- Response DTO의 필수·nullable 자동 추론이 실제 계약과 다를 때만 명시적으로 보완한다.

```java
public record PlaceResponse(
        @Schema(
                description = "장소 ID",
                example = "123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(
                description = "지번 주소",
                example = "서울특별시 종로구 세종로 1-91",
                nullable = true)
        String address
) {
}
```

일반 클래스 DTO도 같은 규칙을 사용한다.

- 어노테이션 묶음과 필드 선언은 붙여 쓰고, 다음 필드와는 빈 줄 한 줄로 구분한다.
- 어노테이션이 없는 선택 필드도 앞뒤 필드와 빈 줄 한 줄로 구분한다.

```java
@NotBlank(message = "{validation.place.name.required}")
private String name;

@Size(max = 500, message = "{validation.place.memo.size}")
private String memo;
```

## Controller, Service와 Repository

- Controller에는 도메인 역할을 나타내는 `@Tag(name, description)`를 작성한다.
- 모든 HTTP API에는 한 문장 `@Operation(summary, description)`을 작성한다.
- Controller와 Service의 공개 기능 메서드에는 역할을 설명하는 한 줄 Javadoc을 작성한다.
- Repository의 공개 조회·저장·변경 메서드에도 DB 대상과 동작이 드러나는 한 줄 Javadoc을 작성한다.
- Javadoc은 `@Transactional`, `@Override` 등 메서드·타입 어노테이션보다 먼저 작성한다.
- `@Operation`은 사용자 관점의 API 동작을, Javadoc은 코드 관점의 메서드 책임을 설명한다.
- 주석은 메서드명을 그대로 번역하지 않고 조회 범위, 권한 조건, 상태 변화 또는 저장 대상을 설명한다.
- 상태 코드와 Response 스키마를 별도로 설명해야 할 때만 `@ApiResponse`를 추가한다.
- 생성자, 단순 private helper와 인터페이스 구현 메서드에는 불필요한 주석을 강제하지 않는다.

```java
/** 여행 경비 API를 제공합니다. */
@Tag(name = "Expense", description = "여행 경비 등록·조회·정산 API")
@RestController
public class ExpenseController {

    /** 여행에 새 경비를 등록합니다. */
    @Operation(summary = "경비 등록", description = "여행 참여자가 사용한 경비를 등록합니다.")
    @PostMapping
    public ExpenseResponse create(...) {
        // ...
    }
}
```

```java
/** 여행 경비와 공동 자금의 영속성 처리를 담당합니다. */
@Repository
public class ExpenseRepository {

    /** 여행에서 삭제되지 않은 경비를 최신 등록 순서로 조회합니다. */
    public List<ExpenseQueryResult> findAll(long tripId) {
        // ...
    }
}
```

## Query Result

- DB 조회 결과 전용 타입은 `query/*QueryResult`에 둔다.
- 한 줄에 컴포넌트 하나만 선언하고, 어노테이션이 없으므로 컴포넌트 사이에 빈 줄을 두지 않는다.
- Validation, OpenAPI, JSON 표현용 어노테이션을 넣지 않는다.
- DB `NOT NULL` 값은 가능한 경우 기본형, nullable 컬럼은 참조 타입으로 표현한다.

```java
public record PlaceQueryResult(
        long id,
        String name,
        Double latitude
) {
}
```

## enum과 일반 Java 파일

- 주요 클래스·record·enum·interface에는 역할을 설명하는 한 줄 Javadoc을 작성한다.
- Javadoc은 `@Service`, `@Repository`, `@Schema` 같은 타입 어노테이션보다 위에 둔다.
- 주석은 구현 코드를 반복하지 않고 해당 타입이 맡는 책임을 한 문장으로 설명한다.
- enum 상수는 기능 그룹별로 한글 설명이 포함된 주석을 둔다.
- 필드 선언, 접근자와 메서드를 한 줄에 여러 개 붙이지 않는다.
- 메서드와 생성자 사이에는 빈 줄을 최소 한 줄 둔다.
- 가독성을 위한 빈 줄은 한 줄을 기본으로 하고, 문맥 구분이 필요해도 연속 두 줄을 넘기지 않는다.
- 생성자와 메서드 인자가 많아 한 번에 읽기 어렵다면 의미 단위로 줄을 나눈다.
- 짧은 값 객체 생성이나 3~5개의 간단한 인자는 한 줄 또는 자연스러운 두 줄로 유지할 수 있다.
- 인자가 많거나 각 인자 표현식이 길고 중첩되어 대응 관계를 찾기 어렵다면 한 줄에 하나씩 배치한다.
- 호출을 세로로 늘린 결과 원래 코드보다 탐색 거리가 커지면 압축된 형태를 우선한다.
- SQL 조회 컬럼은 SQL 본문에서 한 줄에 하나씩 읽을 수 있도록 정렬한다.
- Repository의 `sql`, `params`, `query`, `listOfRows`, `stream`, `map` 등 조회 체인은 단계별로 한 줄씩 배치한다.
- 여러 값이 들어가는 `params`, Query Result 생성자와 매핑 호출을 여러 줄로 나눴다면 인자도 한 줄에 하나씩 배치한다.
- 조회 메서드 안의 Row 변환이 3개 이상의 필드를 조립하거나 타입 변환을 포함하면 `mapXxx` private 메서드로 추출한다.
- 짧은 단일 값 조회나 두 필드 이하의 단순 변환은 인라인 매핑을 허용하되 조회 흐름을 가리지 않아야 한다.

```java
public Optional<EditableScheduleItemQueryResult> lockItem(long tripId, long itemId) {
    return jdbc.sql("""
            SELECT ...
            """)
            .params(
                    tripId,
                    itemId)
            .query()
            .listOfRows()
            .stream()
            .findFirst()
            .map(this::mapEditableItem);
}
```

## 메서드 내부 간격과 주석

- Service의 public 메서드는 검증, 핵심 계산, 저장, 후처리와 응답 조립의 전체 흐름이 한눈에 보이도록 유지한다.
- 단계가 바뀌는 지점에는 빈 줄 한 줄을 두고, 동시성 잠금이나 임시 순번처럼 이유가 필요한 곳에만 짧은 구간 주석을 작성한다.
- 긴 호출·생성자·메서드 선언은 인자 수와 표현식 길이에 따라 한 줄, 의미 단위 묶음 또는 세로 정렬 중 가장 읽기 쉬운 형태를 선택한다.
- 단순 Repository 위임, 한 번만 쓰는 짧은 계산과 저장 인자 조립은 별도 private 메서드로 추출하지 않는다.
- 독립된 업무 규칙이거나 여러 곳에서 재사용되며, 이름만으로 본문보다 의도가 명확해질 때만 private 메서드로 분리한다.
- 함수 분리 때문에 호출부와 구현부를 반복해서 오가야 한다면 한 함수 안에서 논리 구간을 나누는 방식을 우선한다.
- 입력·권한 검증, 핵심 업무 처리, 저장, 응답 변환처럼 책임이 바뀌는 지점에만 빈 줄을 한 줄 둔다.
- 단순 대입문이나 한 흐름의 메서드 체인 사이에는 빈 줄을 넣지 않는다.
- 메서드의 첫 문장 앞이나 마지막 문장 뒤에는 장식용 빈 줄을 넣지 않는다.
- 중괄호 없는 한 줄 `if`는 사용하지 않는다.
- 입력 정규화, 주 조회, 연관 일괄 조회, 상태 변경, 응답 조립처럼 책임이 전환될 때만 빈 줄을 둔다.
- 긴 메서드는 논리 구간마다 `//` 주석을 사용할 수 있으며, 코드를 번역하지 말고 해당 구간이 필요한 이유를 설명한다.
- 트랜잭션 경계, 동시성 잠금, 외부 API 호출 분리, 보상 처리처럼 코드만 보고 의도를 알기 어려운 부분은 주석을 우선한다.
- 메서드 전체 책임은 Javadoc에 쓰고, 세부 구현 순서를 Javadoc에 길게 나열하지 않는다.
- 설명이 길어지는 메서드는 먼저 논리 구간 배치를 정리하고, 그래도 독립 책임이 명확할 때만 private 메서드나 command/model 분리를 검토한다.

```java
/** 공급자 호출 결과가 최신 일정에 해당할 때만 추천 경로를 저장합니다. */
public RouteResponse recommend(...) {
    validateAccess(...);

    // 외부 API 호출 중에는 DB 연결과 행 잠금을 점유하지 않는다.
    RouteCalculation calculation = calculateOutsideTransaction(...);

    return persistIfRevisionMatches(calculation);
}
```

```java
/** 여행 경비의 저장·조회 SQL과 DB Row 매핑을 담당합니다. */
@Repository
public class ExpenseRepository {
}

/** 여행 상태 변경 규칙과 참여자 권한을 처리합니다. */
@Service
public class TripService {
}
```

## 확인

형식 누락은 다음 명령으로 검사한다.

```shell
python scripts/format_unannotated_records.py
python scripts/check_java_layout.py
```

검사기는 DTO·Query Result 형식, 일반 DTO 필드 사이의 간격, Controller의 `@Tag`·`@Operation`, DTO의 `@Schema`, Service·Repository 공개 기능의 Javadoc, 다중 필드 선언, 압축된 한 줄 메서드와 Repository 체인 호출을 찾는다. 인자 배치는 문맥과 표현식 길이에 따라 달라지므로 기계적으로 강제하지 않고 리뷰에서 판단한다. 필드별 OpenAPI 설명과 예시의 필요성도 API 문서 검토로 확인한다.
