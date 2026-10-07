# 작업계획표 (홈 카테고리 순위 API)

> 최종 갱신: 2026-09-26 · 브랜치: `feat/travel-route-demo`(기존 `feat/rankings` 작업 포함) · 상태: **순위 API·여행루트 데모 구현·테스트 완료, 커밋/PR/배포 전**
> 전체 계획(앱 연동, 여행지 연계 흐름, 산출물)은 `../Gayadi-Android/docs/WORK_PLAN.md`를 본다.

## 여행루트 데모 (`feat/travel-route-demo`)

- `POST /api/v1/trips/{tripId}/itinerary-recommendations`: 여행 지역 장소를 거리·이동수단·이용 시간대에 맞게 순서화하고 장소 유형별 체류시간을 반환한다.
- `PUT /api/v1/trips/{tripId}/itinerary-selections/{date}`: 동일한 루트를 재계산한 뒤 해당 날짜의 MAIN 일정 전체를 트랜잭션으로 교체한다.
- 현재 이동시간은 데모용 직선거리·이동수단별 평균 속도 추정치다. 응답의 `estimated=true`로 명시한다.
- 저장된 장소가 2개 이상이면 선택적 Tour API 보강이 불가능해도 로컬 후보만으로 여행루트를 계속 추천한다.
- 장소 추천 Agent가 꺼져 있어도 저장된 공개 장소를 현재 위치에서 가까운 순으로 반환해 `하나씩 고르기` 흐름을 유지한다.
- 서버 전체 테스트 242개가 통과했고, 루트 시간 배치·`variation`에 따른 전체 재추천 및 Tour API 미설정 회귀 테스트를 추가했다.

## 작업 규칙

- 커밋·푸시·PR은 사용자가 명시적으로 요청할 때만 한다.
- `.env`, `secrets/`를 읽지 않는다. 로컬에서 jar를 실행할 때는 저장소 밖 폴더에서 실행한다(`.env` 자동 import 방지). DB는 H2 메모리를 쓴다.

## 구현 범위

- `GET /api/v1/rankings?type=&region=&limit=` (JWT 필요, `limit` 1~20, `region` 50자 이하)
  - 코드: `src/main/java/com/gayadi/server/ranking/`
  - 테스트: `src/test/java/com/gayadi/server/ranking/`

| type | 출처 | 대체 동작 |
|---|---|---|
| ATTRACTION | 데이터랩 `LocgoHubTarService1/areaBasedList1` `hubRank`, TourAPI `searchKeyword2`로 이미지 보강 | TourAPI `areaBasedList2`, `providerDataAvailable=false` |
| FESTIVAL | TourAPI `searchFestival2`, 진행 중 → 시작일 순 | 실패 시 빈 목록, `false` |
| REGION | 데이터랩 `DataLabService/locgoRegnVisitrDDList` 외지인·외국인 일평균 | 빈 목록, `false` |
| RESTAURANT | `user_favorite_places` 찜 수 | 없음 |

- 설정: `datalab.api.key`(`DATALAB_API_KEY`, 없으면 `TOUR_API_KEY`), `hub-base-url`, `visitor-base-url`

## 검증

- `gradlew test --dependency-verification lenient` 242개 통과(순위 12개 포함: 서비스 7, HTTP 4, 찜 통합 1)
- 로컬 jar와 가짜 KTO API로 전 구간을 검증했다(관광지·축제·지역 200, 무토큰 401, 캐시 적중)
- 로컬 H2 서버와 Android 에뮬레이터에서 첫 장소 추가 후 해당 장소 기준 다음 후보 재추천, 하루 루트 생성을 확인했다.
- 실제 데이터랩 응답은 활용신청 승인 전이라 확인하지 못했다. 특히 `signguCd` 없는 광역 조회를 지원하는지 모른다.

## 남은 작업

1. [ ] 사용자 승인 후 커밋과 PR(`feat/rankings` → `dev`)
2. [ ] 개발·운영 배포. 배포 후 `/api/v1/rankings` 200을 확인한다(현재 개발 서버 404).
3. [ ] 공공데이터포털에서 `LocgoHubTarService1`, `DataLabService` 활용신청. 승인 후 실제 필드명을 대조한다.
