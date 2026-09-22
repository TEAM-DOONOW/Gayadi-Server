# 이동수단별 경로 추천

장소를 고르는 단계의 이동시간순 정렬은 [장소찾기 연동 문서](place-travel-time-search.md)를 사용합니다. 아래 API는 이미 정해진 일정 전체의 경로 계산용으로 유지합니다.

`POST /api/v1/trips/{tripId}/route-recommendations`

```json
{
  "type": "ITINERARY",
  "transportMode": "CAR"
}
```

- `transportMode`: `CAR` 또는 `PUBLIC_TRANSIT`. 생략/null은 기존 호환을 위해 `PUBLIC_TRANSIT`으로 처리합니다. 다른 값은 400 응답입니다.
- `type`: 기존 `DEPARTURE`, `ITINERARY`, `HOME` 계약을 유지합니다.
- 자동차는 Kakao Directions, 대중교통은 기존 `route.provider` 설정(`local` 또는 `tmap`)을 사용합니다.
- 반환되는 `stops`는 추천 방문 순서이며 `segments`는 그 순서의 이동 구간입니다. 프론트는 이 순서를 표시해야 합니다. 계산과 저장이 모두 끝난 후 201을 반환합니다.
- 추천 생성·선택·선택 목록의 `transportMode`와 DB 저장 값이 일치합니다.

## 방문 순서

`ITINERARY`는 장소 간 방향별 예상 소요시간을 비용으로 nearest-neighbor 후 2-opt를 적용합니다. 역방향 내부 구간까지 계산하며 기존 순서보다 이동시간이 늘어나는 결과는 채택하지 않습니다. 동일 장소를 여러 번 방문하는 일정도 유지합니다.

날짜별 첫·마지막 장소, 시작 또는 종료 시각이 있는 일정, 숙소, 진행·완료 등 PLANNED가 아닌 일정은 고정합니다. 좌표 없는 일정도 경계로 취급합니다. 고정 지점 사이의 시간 미지정 장소만 재배치하며 날짜 경계를 넘기지 않습니다. 자동 생성 일정도 시각이 이미 채워져 있으면 고정됩니다. 현재 모델에는 예약 시각과 자동 배정 시각을 구별하는 필드가 없습니다.

추천 순서는 경로 데이터에 저장됩니다. 기존 일정 항목의 `sequence_no`와 시각을 자동으로 덮어쓰지는 않습니다. 영업시간 검사나 도착·출발 시각 재배정은 포함하지 않습니다.

여행 전체는 최대 100곳, 하나의 최적화 구간은 고정된 양 끝을 포함해 최대 12곳입니다. 방향별 구간 조회 결과는 요청 내에서 재사용합니다. 요청당 최대 500개 구간 조회와 30초의 추가 조회 시작 제한을 적용하고, 이미 진행 중인 HTTP 호출은 공급자 타임아웃까지 기다릴 수 있습니다. 제한 또는 공급자 오류 발생 시 부분 추천을 저장하거나 기존 추천을 만료시키지 않습니다.

## 자동차 설정과 응답

서버의 `route.kakao.api-key` 또는 `KAKAO_DIRECTIONS_API_KEY`로 키를 주입합니다. 테스트용 주소는 `route.kakao.base-url`로 지정할 수 있습니다. 실제 키는 클라이언트에 전달하지 않습니다.

[Kakao 자동차 길찾기 공식 문서](https://developers.kakaomobility.com/guide/navi-api/directions)의 `priority=TIME`을 사용합니다. 소요시간은 분 단위 올림이며, 최적화도 이 값을 사용합니다. 조회 시점의 예상값으로 미래 출발 시각을 반영한 예측은 아닙니다.

- `provider`: `KAKAO_DIRECTIONS`
- `transferCount`: `0`
- `fare`: 통행료 합계. 유류비·주차비·택시요금은 포함하지 않습니다.
- `options`: 자동차는 실제 계산한 추천 1개를 반환합니다. ID는 출발 `fast`, 일정 `balanced`, 귀가 `home-fast`입니다. 프론트는 반환된 `options`를 기준으로 표시해야 합니다.
- 설정 누락: `503 KAKAO_NOT_CONFIGURED`
- 호출 한도 초과: `429 KAKAO_RATE_LIMITED`
- 이동 가능한 경로 없음: `409 KAKAO_ROUTE_UNAVAILABLE`
- 통신·응답 오류: `502 ROUTE_PROVIDER_FAILED`
- 최적화 구간 초과: `400 ROUTE_OPTIMIZATION_TOO_LARGE`

자동차 조회 실패 시 대중교통이나 직선거리 추정으로 대체하지 않습니다. 대중교통의 기존 로컬 추정/fallback 동작은 유지하며 응답의 `provider`, `configuredProvider`, `fallback`으로 구분합니다.
