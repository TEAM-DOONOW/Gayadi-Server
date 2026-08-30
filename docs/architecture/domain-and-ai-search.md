# 도메인 맵과 AI 검색 설계 초안

이 문서는 GAYADI의 현재 구현 코드와 Flyway 마이그레이션을 기준으로 도메인 경계와 AI 검색 적용 범위를 정리한다.

기존 발표 자료와 설계서에는 이전 스키마, Kotlin, Maven, Milvus를 전제로 한 내용이 남아 있다. 구현 작업의 기준은 현재의 Java 21, Spring Boot 4.1, 단일 Gradle 프로젝트, PostgreSQL/H2, `src/main/resources/db/migration`이다.

## 1. 분류 원칙

- 업무 원본을 누가 소유하는지 기준으로 도메인을 나눈다.
- 상태 변경과 트랜잭션 경계가 다른 기능은 검색 요구가 비슷해도 분리한다.
- 외부 API 어댑터와 AI 오케스트레이션은 원본 업무 도메인으로 취급하지 않는다.
- 검색 인덱스는 원본 데이터가 아니다. 인덱스에는 원본 식별자와 버전만 저장하고, 최종 응답은 원본 DB에서 검증·보강한다.
- 현재는 마이크로서비스로 분리하지 않고 패키지 경계를 가진 모듈러 모놀리스로 유지한다.

## 2. 도메인 맵

| 도메인 | 현재 패키지 | 소유 데이터 | 책임 |
| --- | --- | --- | --- |
| Identity & Social | `auth`, `friendship` | `users`, `social_login_accounts`, `user_devices`, `friendships` | 인증, 사용자 프로필, 친구 관계, 접근 주체 식별 |
| Trip Collaboration | `travel`, `invitation` | `trips`, `trip_participants`, `trip_cities`, `travel_invitations` | 여행 생명주기, 멤버십, 출발 방식, 초대 |
| Traveler Preference | `survey` | `surveys`, `survey_questions`, `survey_question_options`, `survey_attempts`, `question_responses`, `travel_personality_results` | 설문 정의·응답·성향 코드·그룹 성향 계산 |
| Place Catalog & Discovery | `place`, `favorite` | `regions`, `places`, `user_favorite_places` | 공개 장소 원본, 지역·분류·좌표, 찜 |
| Itinerary & Mobility | `schedule`, `route` | `travel_plans`, `travel_plan_items`, `travel_routes`, `travel_supplies` | 일정 생성·편집, 순서, 경로 후보·선택, 준비물 |
| Live Context & Adaptation | `event`, `weather` | `event_observations`, `ai_schedule_change_proposals` | 날씨·혼잡·교통 관측, 영향 판단, 변경 제안·승인 |
| AI Recommendation | `recommendation` | 검색 인덱스와 임베딩 자료 | 후보 검색, 재정렬, 설명 생성. 업무 원본의 소유자는 아님 |
| Supporting / Adapter | `dashboard`, `legal`, `tourapi`, `common`, `config` | `legal_documents`, `notifications` 등 | 조회 조합, 법률 문서, 외부 API 변환, 알림·공통 기능 |

`dashboard`는 여러 도메인을 조합하는 read model이므로 독립 업무 도메인으로 키우지 않는다. `tourapi`, `weather`는 외부 공급자 교체를 위한 어댑터로 둔다. `notifications`와 `travel_supplies`는 스키마가 먼저 존재하고 업무 API가 아직 완성되지 않은 기능으로 표시한다.

## 3. 의존 방향

```text
Identity & Social
        |
Trip Collaboration ---- Traveler Preference
        |                         |
        +------ Itinerary & Mobility <---- Place Catalog & Discovery
                         |
              Live Context & Adaptation

AI Recommendation --(read ports)--> Place / Preference / Trip context
Dashboard ---------(read composition)--> 여러 도메인
```

권장 규칙은 다음과 같다.

- `Place Catalog`은 여행 일정이나 AI 모듈을 호출하지 않는 독립 원본 도메인으로 둔다.
- `Itinerary`는 장소 ID를 참조할 수 있지만 장소의 상세 원본을 소유하지 않는다.
- `Live Context`가 변경 제안을 만들더라도 일정 원본을 직접 무제한 수정하지 않는다. 현재처럼 제안·승인·버전 검사를 거쳐 `Itinerary`를 변경한다.
- `AI Recommendation`은 `JdbcClient`로 모든 테이블을 직접 조회하는 대신, 장기적으로 `PlaceSearchPort`, `TripContextPort`, `PreferencePort` 같은 읽기 포트를 사용한다.
- 사용자의 현재 일정, 권한, 일정 버전은 AI 인덱스에서 읽지 않는다. 해당 요청 시점의 도메인 서비스가 확인한다.

현재 서비스들은 모두 하나의 DB를 공유하고 직접 SQL을 사용하므로 위 경계는 아직 논리적 경계다. 다음 리팩터링의 목표는 패키지 이동보다 먼저 테이블 소유권과 호출 방향을 테스트로 고정하는 것이다.

## 4. 도메인별 검색 적용 범위

| 데이터 | 검색 여부 | 1차 방식 | 이유 |
| --- | --- | --- | --- |
| 공개 `places` | 검색 대상 | lexical + embedding + 거리·지역·분류 필터 | 장소명 같은 정확 검색과 “조용한 실내 카페” 같은 의도 검색이 모두 필요 |
| TourAPI에서 정제한 관광 콘텐츠 | 검색 대상 | lexical + embedding, 출처·지역·유효기간 필터 | 외부 원문을 `places`에 정규화한 뒤 지식 검색 자료로 사용 |
| `legal_documents` | 검색 대상 | lexical + embedding + 문서 버전 필터 | 약관 질문은 정확한 조항과 의미 검색, 답변에는 문서·시행일 인용 필요 |
| 설문 결과·성향 코드 | 일반적으로 비검색 | 구조화된 feature와 규칙 | 8개 결과 코드, 축, 점수는 임베딩보다 결정적 계산이 정확 |
| `trips`, `travel_plans`, `travel_plan_items` | 벡터 검색 금지에 가까움 | 권한 검증 후 SQL | 최신 version과 현재 상태가 중요하고 개인 데이터 접근 통제가 필요 |
| `event_observations`, 날씨 | 벡터 검색 금지 | 최신 관측·TTL·규칙 | “현재 비가 오는가”는 의미 유사도가 아니라 시점과 유효기간 문제 |
| `travel_routes` | 벡터 검색 금지 | 경로 공급자·캐시·SQL | 소요시간, 환승, 출발 시각을 계산해야 함 |
| 사용자·친구·초대 | 벡터 검색 금지 | 정확 검색과 권한 | 개인정보 노출과 동명이인 문제가 있음 |

개인 여행과 정확한 위치를 임베딩 자료에 넣지 않는다. 향후 비공개 자료를 검색해야 한다면 모든 문서에 `ownerUserId`, `tripId`, `visibility`, `sourceUpdatedAt`를 넣고 검색 시점에 ACL 필터를 강제한다.

## 5. BM25만 사용할 것인가

아니다. 검색 방식은 다음 세 계층으로 나누는 것이 적절하다.

1. **Lexical retrieval**
   - 장소명, 지역명, 카테고리, 외부 ID, 고유명사에 강하다.
   - BM25 또는 PostgreSQL full-text search를 사용한다.
   - 한국어 형태소 분석과 동의어 사전이 중요하다.
2. **Dense retrieval**
   - “아이와 가기 좋은 비 오는 날 실내 장소”처럼 표현이 달라도 의도가 같은 질의에 강하다.
   - 한국어를 충분히 평가한 임베딩 모델을 사용한다.
3. **Reranking / business ranking**
   - 상위 후보를 재정렬하되 거리, 영업 여부, 장소 분류, 실내 여부, 이벤트 영향, 사용자 성향을 반영한다.
   - 장소 추천은 초기에는 cross-encoder나 LLM보다 결정적 점수와 거리 필터를 우선한다.

두 검색 결과는 점수의 단위가 다르므로 단순 가중합보다 Reciprocal Rank Fusion(RRF)으로 합치는 것을 1차 방식으로 한다. 필요할 때만 상위 20~50개에 reranker를 적용한다.

### 저장소 선택

임베딩과 벡터 저장소는 현재 구현 범위가 아니다. 현재 장소 추천은 TourAPI 후보를 사용하는 API-first Agent 경로만 제공한다.

| 선택지 | 판단 |
| --- | --- |
| PostgreSQL FTS + pgvector | 현재 DB를 유지하면서 장소 중심 MVP를 만들 때 권장. 운영 컴포넌트가 가장 적다. 단, PostgreSQL `ts_rank`는 엄밀한 BM25가 아니다. |
| OpenSearch | 엄밀한 BM25, 한국어 `nori` 분석기, 복잡한 필터·검색 관측이 제품 요구사항이면 선택한다. PostgreSQL은 원본 DB로 유지한다. |
| Milvus 단독 | dense 검색만 해결한다. BM25와 고유명사 검색을 별도로 추가해야 하므로 현재 단계의 첫 선택으로 권장하지 않는다. |

따라서 **지금은 PostgreSQL + pgvector + lexical 검색으로 시작하고, “엄밀한 BM25와 한국어 분석기”가 필요하다는 평가 결과가 나올 때 OpenSearch를 추가**하는 순서가 안전하다. BM25가 반드시 요구사항이면 처음부터 OpenSearch를 lexical 엔진으로 정하고 pgvector를 같은 검색 계층에서 함께 운영한다.

## 6. 권장 파이프라인

```text
[원본 도메인 DB / 외부 API]
          |
          v
[정규화 + 문서 생성]
  - stable documentId
  - domain / sourceId
  - ACL / region / category / time metadata
  - contentHash / sourceUpdatedAt
          |
          +--> [lexical index]
          +--> [embedding model] --> [vector index]
                                      |
[사용자 질의]
          |
          v
[도메인 라우팅 + 구조화 필터 + 권한 검증]
          |
          +--> lexical topK
          +--> dense topK
                    |
                    v
                 [RRF]
                    |
          [필요 시 reranker / 업무 점수]
                    |
          [원본 DB hydrate + 최종 ACL/freshness 검증]
                    |
          [LLM 설명 또는 구조화된 추천 결과]
```

장소 문서는 장소 하나를 기본 단위로 만들고, 법률·관광 문서는 섹션 또는 의미 단위로 나눈다. 모든 검색 결과는 `placeId`나 `documentId`로 원본을 재조회하며, LLM이 새 ID나 장소를 만들어내지 못하도록 허용된 후보 ID만 반환하게 한다. 현재 `RecommendationService`의 후보 ID 검증 방식은 유지할 가치가 있다.

권장 검색 문서의 최소 메타데이터는 다음과 같다.

```json
{
  "documentId": "place:123",
  "domain": "place",
  "sourceType": "places",
  "sourceId": "123",
  "contentHash": "...",
  "sourceUpdatedAt": "...",
  "embeddingModel": "text-embedding-3-small",
  "embeddingVersion": "v1",
  "regionId": 7,
  "category": "CAFE",
  "visibility": "PUBLIC",
  "status": "ACTIVE"
}
```

## 7. 현재 구현에서 먼저 바꿀 부분

- `PlaceService`의 `LIKE` 검색과 `RecommendationService`의 dense-only 검색을 하나의 장소 검색 포트 뒤로 통합한다.
- 장소 검색은 `ACTIVE`·`PUBLIC`·지역·분류·좌표 범위를 먼저 필터링하고, lexical/dense 후보를 합친 뒤 거리와 영업 조건을 반영한다.
- `PlaceEmbeddingService`를 전체 재생성 전용이 아니라 `contentHash`와 `updatedAt` 기반 upsert/delete 작업으로 바꾼다.
- 벡터 자료에는 `placeId`, 상태, 공개 범위, 원본 갱신 시각을 함께 저장한다.
- TourAPI 조회 결과를 바로 LLM에 넣지 말고 `places` 또는 별도 정규화 저장소에 적재한 뒤 인덱싱한다.
- 일정·이벤트 변경 제안은 검색 결과가 직접 DB를 변경하지 않도록 현재의 승인과 `travel_plans.version` 검사를 유지한다.
- 추천·검색 평가 로그에 질의, 적용된 필터, 후보 ID, 선택 결과, 응답 시간, 인덱스 버전을 남긴다. 개인정보와 정확한 위치는 마스킹한다.

## 8. 단계별 실행 순서

1. 이 문서의 도메인·데이터 소유권을 기준으로 모듈 간 의존 규칙을 정한다.
2. 장소 검색 질의 30~50개와 정답 장소 ID를 수집해 평가 세트를 만든다.
3. 장소에 대해 구조화 필터 + lexical 검색을 먼저 만들고, 기존 `LIKE` 결과와 비교한다.
4. PostgreSQL에 pgvector를 붙여 dense 검색을 추가하고 RRF로 hybrid 검색을 만든다.
5. `Recall@K`, `MRR`, `nDCG@K`, zero-result 비율, p95 latency를 도메인별로 측정한다.
6. 평가에서 고유명사·한국어 형태소 검색이 부족할 때만 OpenSearch BM25/nori를 도입한다.
7. 장소 검색이 안정화된 뒤 관광 콘텐츠, 마지막으로 버전이 있는 법률 문서를 별도 인덱스로 확장한다.
8. 일정·날씨·경로는 검색 인덱스에 넣지 않고 도메인 조회와 규칙/도구 호출로 AI 파이프라인에 연결한다.

## 9. 현재 상태와 주의점

- 현재 구현은 Groq가 검색 계획을 만들고 TourAPI 후보를 서버가 취합하는 API-first Agent 경로다. 임베딩 검색은 향후 설계 후보일 뿐 운영 기능으로 간주하지 않는다.
- 현재 파이프라인에는 BM25, hybrid fusion, 문서 버전, 증분 삭제, 검색 평가 세트가 없다.
- `docs/presentation`의 Milvus 설명은 현재 구현과 일치하지 않으므로 운영 저장소를 확정할 때 함께 갱신해야 한다.
- AI 추천 요청의 외부 처리 동의는 입력 검증에 사용되지만 동의 이력과 검색·모델 버전 감사 체계는 별도로 보강해야 한다.

## 10. 여행별 후보 조회

장소 후보 조회는 `장소 검색어`만 받는 API와 분리한다. 여행별 API는 `tripId`를 기준으로 서버가 지역, 참여자 수, 설문 결과, 일정, 날씨를 조합해야 한다. 클라이언트가 프로필이나 날씨를 다시 보내게 하면 값이 서로 달라지거나 하드 제약을 우회할 수 있다.

권장 API 형태는 다음과 같다.

```http
POST /api/v1/trips/{tripId}/place-candidates
```

```json
{
  "purpose": "RAIN_ALTERNATIVE",
  "anchorPlaceId": 123,
  "targetAt": "2026-08-22T14:00:00+09:00",
  "query": "아이들과 함께 오래 머물 수 있는 실내 장소",
  "constraints": {
    "categoryCodes": ["CULTURE", "SHOPPING", "CAFE"],
    "maxDistanceKm": 15,
    "limit": 20
  }
}
```

`purpose`는 최소한 `PLACE_RECOMMENDATION`, `PLAN_GENERATION`, `RAIN_ALTERNATIVE`를 구분한다. `indoorRequired`, 지역, 실제 참여자 수, 그룹 성향, 날씨 정책은 서버가 계산한다. 사용자가 실내만 원한다고 명시한 경우에는 하드 조건으로 승격할 수 있지만, 폭우 등 시스템이 실내를 강제하는 경우에는 클라이언트가 해제할 수 없어야 한다.

### 10.1 요청 컨텍스트 만들기

후보 검색 전에 다음 값을 한 번의 read model로 만든다.

| 값 | 현재 데이터 또는 공급자 | 용도 |
| --- | --- | --- |
| 여행 지역 | `trips.region_id`, `trip_cities` | 장소 지역 하드 필터 |
| 참여 인원 | `trip_participants`의 `JOINED` 수 | 그룹 적합도·수용 인원 판단 |
| 그룹 성향 | `survey_attempts`의 축별 응답 분포 | 장소 선호와 활동 강도 점수 |
| 여행 일정 | `travel_plans`, `travel_plan_items` | 이미 방문·예약된 장소 제외, 시간 충돌 방지 |
| 날씨 정책 | 기상 API 또는 `event_observations` | `OUTDOOR_OK`, `INDOOR_PREFERRED`, `INDOOR_REQUIRED` |
| 기준 위치 | 현재 일정 장소 또는 여행 대표 좌표 | 거리와 이동시간 계산 |

현재 `SurveyService.groupProfile()`은 대표 `result_code` 하나와 분포만 반환한다. 그룹 후보 점수에는 대표 코드 하나보다 `NATURE/CITY`, `ACTIVE/RELAXED`를 각각 인원수로 집계한 축별 분포를 사용한다. `PLANNED/SPONTANEOUS`는 장소 종류보다 하루 시작 시각, 장소 수, 이동 여유에 반영한다.

### 10.2 울산에 비가 오는 경우

`RAIN_ALTERNATIVE`의 검색 순서는 다음과 같다.

1. `trips.region_id = 울산`이고 `ACTIVE`·`PUBLIC`인 장소만 남긴다.
2. 날씨 정책이 `INDOOR_REQUIRED`이면 `indoor = TRUE`를 하드 필터로 적용한다.
3. 현재 일정에서 이미 완료했거나 사용 중인 장소를 제외한다.
4. `anchorPlaceId`가 있으면 기준 장소에서 이동 가능한 거리 또는 시간으로 제한한다.
5. 그룹 인원에 맞는 수용 규모, 체류 시간, 예약 필요 여부를 확인한다.
6. 성향과 활동량으로 `CULTURE`, `SHOPPING`, `CAFE`, `RESTAURANT` 등의 순서를 점수화한다.
7. 같은 종류의 장소만 상위에 몰리지 않도록 카테고리와 위치를 분산한다.
8. 상위 10~20개만 Groq에 전달해 최종 추천 이유와 순서를 생성한다.

폭우인데 실내 결과가 없다고 LLM이 임의로 야외 장소를 섞으면 안 된다. 후보가 없을 때는 다음 순서로만 완화한다.

1. 같은 지역의 실내 장소 범위를 넓힌다.
2. 인접 지역까지 반경을 넓힌다.
3. 사용자가 허용한 경우에만 지붕이 있는 전천후 장소를 추가한다.
4. 그래도 없으면 후보 부족을 명시하고 사용자에게 조건 완화를 요청한다.

### 10.3 울산 여행의 일반 장소 추천

`PLACE_RECOMMENDATION`이나 `PLAN_GENERATION`은 비가 오지 않는다고 가정해 야외를 허용하되, 성향과 그룹 정보를 soft ranking에 사용한다.

- `NATURE`가 많은 그룹은 자연·전망·공원 계열을 우선한다.
- `CITY`가 많은 그룹은 문화·쇼핑·카페·도심 명소를 우선한다.
- `ACTIVE`가 많은 그룹은 이동과 활동량이 큰 장소를 우선한다.
- `RELAXED`가 많은 그룹은 체류 시간이 길고 이동이 적은 장소를 우선한다.
- `PLANNED`가 많은 그룹은 운영시간과 예약 가능성이 명확한 장소를 우선한다.
- `SPONTANEOUS`가 많은 그룹은 예약 제약이 낮고 즉시 방문 가능한 장소를 우선한다.
- 인원이 많으면 작은 매장보다 단체 수용 가능 장소, 분산 가능한 장소, 예약 가능 장소를 우선한다.

성향은 필터가 아니라 점수다. 예를 들어 `NATURE` 그룹이라도 비가 오면 자연 선호를 완전히 버리는 대신 실내 체험, 수족관, 식물·전시 공간처럼 자연 관심을 일부 보존하는 후보를 우선할 수 있다. 이런 연결은 LLM에게 맡기기보다 장소 태그와 점수 규칙으로 먼저 표현한다.

### 10.4 하드 필터와 점수 분리

```text
하드 필터
  region, ACTIVE, visibility, indoorRequired, 사용 가능 시간,
  접근성 필수 조건, 제외 장소, 권한

업무 점수
  weatherFit, preferenceFit, groupFit, distanceFit,
  routeFit, freshness, categoryDiversity

LLM
  허용된 후보 ID 중 최종 순서와 자연어 이유 생성
```

예시 점수는 다음처럼 시작할 수 있지만, 고정값으로 간주하지 말고 평가 데이터로 조정한다.

```text
score = 0.30 * preferenceFit
      + 0.25 * groupFit
      + 0.20 * distanceFit
      + 0.15 * routeFit
      + 0.10 * freshness
```

날씨가 `INDOOR_REQUIRED`인 경우 `weatherFit`은 점수로 보상하지 않고 하드 필터로 처리한다. `indoor = NULL`인 장소를 실내로 추정하지 않는다.

## 11. 장소 데이터 보강 없이는 그룹 추천이 제한된다

현재 `places`에는 `indoor`가 있지만 수용 인원, 단체 적합성, 평균 체류 시간, 예약 필요 여부, 접근성, 가격대, 어린이 적합성, 운영시간의 구조화된 값이 부족하다. `basic_info` JSON 문자열에 모든 판단 정보를 넣고 `LIKE`로 검색하는 방식은 유지하지 않는다.

최소한 다음 속성을 구조화한다.

```text
groupFriendly
capacityMin / capacityMax
estimatedDurationMinutes
reservationRequired
familyFriendly
accessibilityLevel
priceLevel
pace
weatherSensitivity
```

기존 장소 테이블에 칼럼을 추가하거나 `place_traits(place_id, trait_code, numeric_value, boolean_value)` 같은 별도 테이블을 사용할 수 있다. 검색 빈도가 높은 `indoor`, `estimated_duration_minutes`, `capacity_max`, `reservation_required`는 칼럼으로 두는 편이 낫다.

## 12. 외부 관광 API의 위치

목록 수집 결과는 `TourPlace`로 반환되고, Agent의 상위 후보는 상세 정보와 함께 `places`에 스냅샷으로 저장된다. 전체 지역 동기화가 필요할 때는 다음 전략을 선택한다.

- 여행 생성 또는 일정 생성 전에 TourAPI 결과를 `places`에 upsert하고 인덱싱한다.
- 초기에는 요청 시 TourAPI를 호출하되, 응답을 정규화해 후보로만 사용하고 필요하면 저장한다.

운영 권장 방식은 첫 번째다. `(source, source_place_id)`를 자연키로 두고 수정 시 장소 원본을 갱신한 뒤 검색 문서를 증분 갱신한다. 현재 Agent는 선택 가능한 상위 후보만 저장하며, 전체 지역을 미리 채우는 동기화 작업은 별도 운영 작업이다.

또한 현재 시드 지역은 서울 중심이고, 여행 생성 시 없는 도시를 `regions`에 이름만 추가한다. 울산 같은 지역은 지역 좌표와 TourAPI 지역 코드(`lDongRegnCd`, `lDongSignguCd`) 매핑까지 저장해야 안정적으로 후보를 조회할 수 있다.

### 12.1 TourAPI 목록과 상세 조회

TourAPI의 목록 API는 한 번에 전체 데이터를 보내는 방식이 아니라 `numOfRows`, `pageNo`로 나누어 반환한다. 현재 어댑터는 이를 `TourListResponse(items, totalCount, pageSize, nextCursor)`로 감싼다.

| 목적 | 현재 서버 경로 | 외부 오퍼레이션 | 주요 질의값 |
| --- | --- | --- | --- |
| 지역 전체 후보 | `/api/v1/tour/areas` | `areaBasedList2` | 시도·시군구 코드, 콘텐츠 타입, 분류 코드 |
| 기준 장소 주변 후보 | `/api/v1/tour/locations` | `locationBasedList2` | 경도·위도, 반경(m), 콘텐츠 타입 |
| 이름·주제 후보 | `/api/v1/tour/keywords` | `searchKeyword2` | `keyword`, 지역·분류 코드 |
| 기간 행사 | `/api/v1/tour/festivals` | `searchFestival2` | 시작일·종료일, 지역·분류 코드 |
| 숙박 | `/api/v1/tour/stays` | `searchStay2` | 지역·분류 코드 |

예를 들어 울산 전체 후보를 모을 때는 먼저 지역 코드 조회로 울산의 `lDongRegnCd`와 시군구 코드를 확인한 뒤 `areaBasedList2`를 호출한다. `contentTypeId`를 관광지(12), 문화시설(14), 행사(15), 레포츠(28), 쇼핑(38), 음식점(39)처럼 나누어 여러 번 조회할 수 있다. 각 응답에서 `nextCursor`가 `null`이 될 때까지 페이지를 이어 받아야 한다.

현재 `TourPlace` 목록 항목에는 `contentId`, `contentTypeId`, `title`, 주소, 좌표, 이미지, 전화번호, 지역·분류 코드, 수정 시각, 행사 기간 등의 요약값이 들어온다. 이 값만으로 지역, 거리, 카테고리, 최신성 후보를 만들 수 있지만 실내 여부, 운영시간, 이용요금, 예상 체류시간, 단체 적합성은 충분하지 않을 수 있다.

따라서 후보 수집은 다음처럼 두 단계로 한다.

```text
목록 API
  -> contentId 중복 제거
  -> 지역·콘텐츠 타입·좌표·수정일 필터
  -> 필요한 상위 후보만 선택

상세 API
  -> detailCommon2
  -> detailIntro2
  -> 필요 시 detailInfo2 / detailImage2
  -> places와 place_traits에 upsert
```

상세 API를 모든 결과에 무제한 호출하지 않는다. 지역 목록을 먼저 수집하고, 변경된 `contentId` 또는 상위 후보만 상세 조회한다. 장기적으로는 `areaBasedSyncList2`와 수정 시각을 이용한 일일 동기화가 적합하다.

TourAPI의 `keyword`는 제목·관광 콘텐츠 기준의 문자열 검색이지 사용자 성향이나 “비 오는 날 단체로 적합함”을 판단하는 의미 검색이 아니다. 따라서 API는 원천 후보 수집에 사용하고, 날씨·그룹 성향·인원수 판단은 GAYADI의 정규화·점수화 계층에서 수행한다.

## 13. LLM에 전달하는 후보 형식

LLM에는 원본 API 응답 전체가 아니라 검증된 후보 요약만 전달한다.

```json
{
  "request": {
    "destination": "울산",
    "weatherPolicy": "INDOOR_REQUIRED",
    "memberCount": 5,
    "profileAxes": {
      "place": {"NATURE": 3, "CITY": 2},
      "energy": {"ACTIVE": 4, "RELAXED": 1}
    }
  },
  "candidates": [
    {
      "placeId": 321,
      "name": "예시 장소",
      "category": "CULTURE",
      "indoor": true,
      "estimatedDurationMinutes": 120,
      "distanceKm": 4.2,
      "reasonCodes": ["INDOOR", "GROUP_FIT", "CITY_PREFERENCE"]
    }
  ]
}
```

LLM 출력은 `placeId`, 순서, 한 줄 이유, 주의사항만 허용한다. 서버는 반환된 ID가 실제 후보에 포함되는지, 여전히 활성·공개인지, 여행 지역과 맞는지 다시 확인한 뒤 일정 초안으로 저장한다.

## 14. Agent가 TourAPI 질의를 개선하는 구조

GAYADI의 Agent는 단순한 후보 판정기가 아니다. 사용자 의도와 여행 상황을 받아 TourAPI 검색 계획을 만들고, 결과가 부족하면 검색어·콘텐츠 타입·검색 방식을 개선한 뒤 다시 조회할 수 있어야 한다. 단, Agent가 외부 API를 무제한 직접 호출하지 않고 서버가 제공하는 제한된 도구를 호출하게 한다.

### 14.1 두 가지 온라인 Agent

| Agent | 입력 | 역할 | 출력 |
| --- | --- | --- | --- |
| Destination Recommendation Agent | 여행지, 멤버 성향·인원, 날짜, 선호 | 여러 TourAPI 질의 계획 생성, 후보 취합·평가, 추천 코스 초안 생성 | 추천 장소·순서·이유 |
| Situation Response Agent | 현재 일정, 날씨·교통·혼잡 이벤트, 대중교통 누락, 기존 장소 | 영향을 받은 일정만 식별, 대체 장소 검색 질의 재작성, 변경안 생성 | `ai_schedule_change_proposals` 초안 |

두 Agent는 같은 `TourSearchTool`을 사용한다. 상황 대처 Agent는 일정 원본을 직접 수정하지 않고 현재 구현의 변경 제안·승인·`travel_plans.version` 흐름으로 연결한다.

### 14.2 Agent의 검색 계획

울산에 비가 오는 경우 Agent가 내부적으로 만들 수 있는 계획은 다음과 같다.

```json
{
  "requiredConstraints": ["REGION_MATCH", "ACTIVE", "PUBLIC", "INDOOR"],
  "queries": [
    {
      "operation": "AREA",
      "keywords": ["울산 실내 문화시설", "울산 박물관", "울산 실내 체험"],
      "contentTypeIds": ["14"]
    },
    {
      "operation": "AREA",
      "keywords": ["울산 쇼핑몰", "울산 실내 가족 체험"],
      "contentTypeIds": ["38", "39"]
    },
    {
      "operation": "LOCATION",
      "contentTypeIds": ["14", "38", "39"],
      "radiusMeters": 15000,
      "anchorPlaceId": 123
    }
  ],
  "maxSearchRounds": 2
}
```

Agent가 개선하는 것은 `keyword`, 콘텐츠 타입 조합, `AREA`·`LOCATION`·`KEYWORD` 선택, 검색어의 구체성이다. `lDongRegnCd`, `lDongSignguCd`, 페이지 번호, 인증키는 서버가 지역 매핑과 페이지네이션을 통해 채운다.

### 14.3 검색 도구 경계

서버가 노출하는 도구는 raw HTTP 호출기가 아니라 도메인 도구여야 한다.

```text
searchTourPlaces(
  operation,
  regionName,
  keywords,
  contentTypeIds,
  anchorPlaceId,
  radiusMeters,
  maxPages
)
```

도구 실행기는 다음을 강제한다.

- 여행 지역 밖의 지역 코드를 거부한다.
- 허용된 TourAPI 오퍼레이션과 콘텐츠 타입만 실행한다.
- 검색어·페이지 수·전체 호출 횟수에 상한을 둔다.
- 페이지를 내부에서 순회하고 `contentId` 기준으로 중복 제거한다.
- 요청 제한, timeout, 캐시, 재시도를 적용한다.
- 결과를 `TourPlace` 요약형으로 반환하고 API 키나 원본 인증 정보는 노출하지 않는다.
- `INDOOR_REQUIRED`, 접근성 필수 등 하드 조건은 도구 실행 후에도 서버가 재검증한다.

Agent는 도구 결과의 `totalCount`, 빈 필드 비율, 후보 수, 중복률을 보고 검색어를 한두 번만 개선한다. 결과가 충분하면 더 호출하지 않는다. 무제한 ReAct 루프는 외부 API 비용과 응답 시간을 통제할 수 없으므로 사용하지 않는다.

### 14.4 추천 Agent 실행 순서

```text
1. 서버가 RecommendationContext를 만든다.
2. Agent가 SearchPlan을 만든다.
3. TourSearchTool이 목록 API를 여러 번 실행한다.
4. 서버가 contentId를 합치고 상세 API를 제한적으로 조회한다.
5. 서버가 지역·날씨·운영시간·거리·인원 조건을 하드 필터링한다.
6. 후보가 부족하면 Agent가 검색어와 API 방식을 한 번 개선한다.
7. 상위 후보를 Agent에 다시 전달해 장소 순서와 이유를 생성한다.
8. 서버가 ID·상태·권한·일정 충돌을 검증하고 초안으로 저장한다.
```

이 구조에서는 Agent가 실제로 TourAPI 질의를 개선하고 여러 번 호출하지만, 검색 실행·보안·하드 조건·최종 저장은 서버가 통제한다. 따라서 Agent의 추론 능력과 서비스의 재현성·안전성을 동시에 확보할 수 있다.
