# 장소찾기에서 이동시간순 정렬

기존 `GET /api/v1/places`의 정렬 옵션으로 제공합니다. 별도의 추천 생성이나 추천 선택 요청 없이, 장소를 고르는 단계에서 이동시간을 비교합니다. 검색은 일정 항목과 저장된 경로를 변경하지 않습니다.

## Android 연동 순서

1. 일정 끝에 추가한다면 같은 날짜의 마지막 방문지를 이전 장소로 사용합니다.
2. 일정 중간에 추가한다면 앞·뒤 방문지 좌표를 함께 보냅니다.
3. 앞선 방문지가 없다면 숙소 좌표를 사용할 수 있습니다. 좌표를 정할 수 없으면 여행 지역을 `region`으로 보내 기존 검색을 사용합니다.
4. 사용자가 선택한 `CAR` / `PUBLIC_TRANSIT` / `WALK` / `BICYCLE`을 `transportMode`로 보냅니다. 대중교통은 이전 일정 종료 시각을 `departureAt`, 후보 체류시간을 `visitDurationMinutes`로 전달합니다.
5. 응답의 `items` 순서대로 후보를 표시하고 `travelTime.durationMinutes`를 ‘자동차 약 10분’처럼 표시합니다. 중간 삽입은 `additionalDurationMinutes`를 함께 표시할 수 있습니다.
6. 선택한 후보의 `id`, `latitude`, `longitude`를 사용합니다. 제목을 Kakao `keywordSearch`로 다시 검색해 첫 결과로 대체하지 않습니다.

지도 SDK는 후보와 선택 장소를 표시하는 용도로 유지합니다. Android 저장소의 실제 화면 수정은 별도로 필요합니다. 기존 경로 추천 API는 이전 클라이언트와 전체 동선 기능의 호환성을 위해 유지합니다.

## 요청

```http
GET /api/v1/places?query=카페&region=1&category=CAFE&sort=TRAVEL_TIME&transportMode=CAR&originLatitude=37.5665&originLongitude=126.9780&limit=10
```

인증된 사용자의 기존 Bearer 인증을 사용합니다. Swagger에서는 Authorize에 accessToken을 입력한 뒤 실행합니다. 장소찾기의 OpenAPI는 선택적 Bearer 인증을 선언하므로 일반 검색은 공개로 유지하면서 입력한 토큰을 전달할 수 있습니다. `query`, `region`, `category` 필터는 기존 공개 장소 검색과 동일합니다. 현재 DB에 저장된 공개·활성 장소가 대상이며, 이 검색 자체가 Kakao/TourAPI에서 새로운 장소를 수집하지는 않습니다. 성향 추천 Agent와도 별개입니다.

| 필드 | 의미 |
| --- | --- |
| `sort` | `RECENT`(기본) 또는 `TRAVEL_TIME` |
| `transportMode` | `CAR`, `PUBLIC_TRANSIT`(기본), `WALK`, `BICYCLE` |
| `originLatitude`, `originLongitude` | 이전 장소 또는 숙소의 좌표. 반드시 한 쌍으로 전달 |
| `nextLatitude`, `nextLongitude` | 다음 장소의 좌표. 일정 중간 삽입일 때 전달 |
| `limit` | 반환 개수, 1–50, 기본 20. 이동시간 비교 후보는 최대 20곳 |
| `departureAt` | 이전 장소에서 출발할 예정 시각. ISO 8601 UTC 오프셋 필수. 생략하면 요청 처리 시 현재 한국 시각을 모든 후보에 동일하게 적용 |
| `transitPreference` | `FASTEST`(기본): 시간→환승 횟수 순. `FEWER_TRANSFERS`: 환승 횟수→시간 순으로 TMAP 경로를 선택 |
| `visitDurationMinutes` | 후보 체류시간, 0–1440분, 기본 0. 다음 구간의 조회 시각에 반영 |
| `cursor` | 기존 `RECENT` 검색에서만 사용. 이동시간순 검색에 보내면 400 |

`TRAVEL_TIME`이어도 이전 장소 좌표가 없으면 기존 검색으로 처리하며 `ranking.sort=RECENT`를 반환합니다. 다음 장소만 전달하면 400입니다. 좌표 범위·누락·유효하지 않은 이동수단도 400으로 처리합니다. 이동시간 계산을 요청하는 경우 로그인하지 않았으면 401입니다.

## 비교 방식

- 다음 장소가 없으면 **이전 장소 → 후보**의 이동시간이 짧은 순서입니다.
- 다음 장소가 있으면 **이전 → 후보 → 다음**에서 **이전 → 다음**의 직행 시간을 뺀 추가 이동시간이 짧은 순서입니다. 후보에서 다음 장소까지는 정방향으로 계산합니다.
- 같은 시간이면 장소 ID 오름차순으로 정렬합니다.
- 외부 API 호출량을 제한하기 위해 검색 조건 전체에서 좌표상 가까운 후보 최대 20곳을 먼저 추립니다. 중간 삽입이면 양쪽 기준점까지의 거리 합으로 추립니다. 따라서 전체 검색 결과에 대한 전역 최단시간 순위를 보장하지 않습니다.
- 이전/다음 장소와 좌표가 같은 후보는 제외합니다.
- 계산은 동시에 최대 4개 후보씩 처리하고, 대기열과 요청 시간을 제한합니다. 조회 결과를 기다리는 전체 예산은 30초입니다. 이미 시작한 공급자 호출은 취소 요청 후 자체 타임아웃까지 지속될 수 있습니다.

## 응답 필드 예시

기존 장소 정보에 `travelTime`이 추가됩니다.

```json
{
  "travelTime": {
    "transportMode": "CAR",
    "durationMinutes": 10,
    "onwardDurationMinutes": 7,
    "additionalDurationMinutes": 5,
    "configuredProvider": "KAKAO_DIRECTIONS",
    "fallback": false
  }
}
```

다음 장소가 없으면 `onwardDurationMinutes`와 `additionalDurationMinutes`는 null입니다. 분 단위 예상값을 사용합니다. 외부 경로 응답이나 분 단위 변환의 차이로 추가 이동시간은 음수가 될 수도 있으며, 원래 계산값을 유지합니다.

페이지 응답은 다음 정보를 포함합니다.

```json
{
  "nextCursor": null,
  "hasNext": false,
  "ranking": {
    "sort": "TRAVEL_TIME",
    "evaluatedCandidates": 20,
    "limited": true
  }
}
```

이동시간순은 후보 추천 목록이며 ID 커서 페이징을 제공하지 않습니다. `limited=true`는 후보 상한 또는 요청한 반환 개수 때문에 일부만 제공했다는 뜻입니다. 더 다양한 결과가 필요하면 검색어·지역·카테고리를 조정합니다. 기존 최신순 검색의 ID 커서 페이징은 유지합니다.

명시적으로 이동 경로가 없는 후보는 제외합니다. 인증 오류·호출 한도·통신 실패 등은 오류 응답으로 반환하고 불완전한 순위를 정상 결과로 반환하지 않습니다. 대중교통의 로컬 추정과 fallback 설정은 기존 동작을 따릅니다. `configuredProvider=LOCAL_ESTIMATE` 또는 `fallback=true`이면 프론트는 실제 교통 조회 결과와 구별해 추정 시간으로 표시해야 합니다.

자동차 설정은 [이동수단별 경로 연동 문서](route-transport-mode.md)를 참고합니다.

## 예정 시각과 대중교통 경로 선택

```http
GET /api/v1/places?sort=TRAVEL_TIME&transportMode=PUBLIC_TRANSIT&originLatitude=37.5665&originLongitude=126.9780&nextLatitude=37.57&nextLongitude=126.99&departureAt=2026-10-01T10:00:00%2B09:00&transitPreference=FEWER_TRANSFERS&visitDurationMinutes=60
```

오프셋의 `+`는 URL에서 `%2B`로 인코딩합니다. `departureAt`은 후보에 도착할 시각이 아니라 **이전 방문지에서 출발할 시각**입니다. 시간대 없는 값은 400으로 처리합니다. 한국 시각으로 변환한 연도는 TMAP 허용 범위인 1900–9999여야 합니다.

TMAP의 최대 10개 반환 경로 중 유효한 소요시간·환승 횟수를 가진 경로를 비교합니다. `legs[].service=0`인 구간이 있는 경로는 제외합니다. 운행 경로가 하나도 없으면 해당 후보는 제외하며, 이를 로컬 추정으로 대체하지 않습니다. 경로 선택은 초 단위 원본 시간을 비교하고 최종 응답만 분 단위로 올립니다.

중간 삽입의 다음 구간은 `departureAt + 이전→후보 이동시간 + visitDurationMinutes`에 조회합니다. 직접 이동 기준은 처음 출발 시각에 한 번 조회합니다. `additionalDurationMinutes`는 두 경로의 **이동시간 차이**이며 체류시간 자체는 더하지 않습니다. 체류시간은 운행 여부 조회 시각에 영향을 줍니다. 각 구간별로 경로를 선택하므로 전체 여정의 전역 최적 경로를 보장하지 않습니다. 장소 후보의 최종 정렬 기준은 선택된 경로의 이동시간이며, 최소 환승 옵션은 각 후보의 대중교통 경로를 고르는 데 적용합니다.

추가 응답 필드:

- `travelTime.departureAt`: 모든 후보에 공통으로 적용한 출발 기준 시각
- `travelTime.onwardDepartureAt`: 후보 체류 후 다음 구간의 출발 시각. 다음 장소가 없으면 null
- `travelTime.transferCount`: 후보 경유 구간들의 총 환승 횟수
- `travelTime.transitPreference`: 대중교통 경로 선택 기준. 자동차면 null
- `travelTime.scheduledTimeApplied`: TMAP에서 출발 시각을 전달해 조회했고 추정 fallback을 사용하지 않았는지 여부. 로컬 추정과 현재 Kakao 자동차 공급자는 false

[TMAP 공식 문서](https://tmap-public-skopenapi.readme.io/reference/대중교통-api)에 따르면 `searchDttm`은 지정한 시각의 **운행 여부 안내**를 위한 값입니다. 미래 혼잡도·정확한 대기시간과 도착시간 예측을 보장하는 기능으로 표시하면 안 됩니다. 실제 TMAP 사용은 서버의 `route.provider=tmap` 설정과 API 키가 필요합니다.

이 시각·선호도 입력 계약은 장소찾기 API에 적용됩니다. 기존 전체 경로 추천 API는 별도 출발 시각 입력 없이 기존 호출을 유지하며, TMAP 경로 선택 기본값은 FASTEST입니다.

## 도보·자전거 필터

`transportMode=WALK` 또는 `transportMode=BICYCLE`을 지원합니다.
`sort=TRAVEL_TIME`과 출발 좌표를 보내면 이동시간순으로 정렬하며, 다음 장소 좌표를 보내면 추가 이동시간도 계산합니다.
도보 시속 4km, 자전거 시속 15km와 직선거리를 이용한 추정치입니다. 실제 보행·자전거 도로 연결 여부는 반영하지 않습니다.
응답의 `configuredProvider`는 `LOCAL_ESTIMATE`이며 `scheduledTimeApplied`는 false입니다.
