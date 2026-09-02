# GAYADI 프론트엔드 API 명세

최종 수정일: 2026-09-02
대상: `Gayadi-Android` 장소·혼잡도 및 AI Agent API 연동

## 1. 기본 정보

- Base URL: Android의 `TOUR_API_BASE_URL`
- 로컬 Android Emulator: `http://10.0.2.2:8080`
- Content-Type: `application/json`
- 문자 인코딩: UTF-8
- 인증: 장소·혼잡도 통합 조회는 인증 불필요
- Swagger UI: `{BASE_URL}/api/docs`
- OpenAPI JSON: `{BASE_URL}/api/openapi`

실제 키와 서버 주소는 저장소에 커밋하지 않습니다. `TOUR_API_BASE_URL`은 GAYADI 백엔드 주소와 동일하게 설정합니다.

## 2. Android 장소·혼잡도 조회

기존 Android 경로를 유지한 호환 API입니다.

```http
GET /api/v1/tour/areas
```

### Query parameters

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---:|:---:|---:|---|
| `regionName` | string | O | - | 앱에서 선택한 국내 여행 지역명 |
| `pageSize` | integer | X | `20` | 1~20 |
| `targetDate` | `YYYY-MM-DD` | X | 서버의 한국 날짜 | 혼잡도 예측 기준일 |
| `contentTypeId` | string | X | 전체 | 관광 타입 |
| `lclsSystm1` | string | X | - | 분류체계 대분류 |
| `lclsSystm2` | string | X | - | 분류체계 중분류 |
| `lclsSystm3` | string | X | - | 분류체계 소분류 |

`cursor`와 `arrange`는 이전 Android 호출과의 호환을 위해 받을 수 있지만 현재 결과에는 영향을 주지 않습니다. `nextCursor`는 항상 `null`입니다.

관광 타입 값:

| 값 | 의미 |
|---:|---|
| `12` | 관광지 |
| `14` | 문화시설 |
| `15` | 축제·공연·행사 |
| `25` | 여행코스 |
| `28` | 레포츠 |
| `32` | 숙박 |
| `38` | 쇼핑 |
| `39` | 음식점 |

호출 예시:

```http
GET /api/v1/tour/areas?pageSize=20&regionName=%EC%84%9C%EC%9A%B8&targetDate=2026-09-01&contentTypeId=12
Accept: application/json
```

### 성공 응답

```json
{
  "items": [
    {
      "contentId": "126508",
      "contentTypeId": "12",
      "title": "경복궁",
      "address": "서울특별시 종로구 사직로 161",
      "addressDetail": "",
      "firstImage": "https://example.com/image.jpg",
      "mapX": "126.976993",
      "mapY": "37.578822",
      "lDongRegnCd": "11",
      "lDongSignguCd": "110",
      "lclsSystm1": "NA",
      "lclsSystm2": "NA01",
      "lclsSystm3": "NA010100",
      "crowdLevel": "CROWDED",
      "concentrationScore": 76,
      "crowdSource": "KTO_TOURIST_CONCENTRATION_FORECAST",
      "crowdEstimated": true,
      "crowdProviderDataAvailable": true,
      "crowdConfidence": "MEDIUM",
      "crowdMessage": "2018년 이후 이동통신 방문 패턴을 기반으로 한 향후 30일 상대 집중률입니다.",
      "crowdTargetDate": "2026-09-01"
    }
  ],
  "totalCount": 1,
  "pageSize": 20,
  "nextCursor": null,
  "regionName": "서울",
  "targetDate": "2026-09-01"
}
```

### 혼잡도 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `crowdLevel` | enum | `RELAXED`, `NORMAL`, `CROWDED` |
| `concentrationScore` | integer | 0~100 상대 집중률 |
| `crowdSource` | enum/string | 예측값의 출처 |
| `crowdEstimated` | boolean | 실시간 인원수가 아닌 예측·추정값 여부. 현재 항상 `true` |
| `crowdProviderDataAvailable` | boolean | 공공 예측 자료 사용 여부 |
| `crowdConfidence` | enum | `LOW`, `MEDIUM`, 향후 `HIGH` 가능 |
| `crowdMessage` | string | 사용자 안내용 근거 설명 |
| `crowdTargetDate` | date | 예측 기준일 |

`crowdSource` 처리 기준:

- `KTO_TOURIST_CONCENTRATION_FORECAST`: 관광지 직접 공공 예측값
- `KTO_DISTRICT_CONCENTRATION_FORECAST`: 해당 시군구 평균 공공 예측값
- `CALENDAR_HEURISTIC`: 공공 예측값이 없어 요일·시간대로 계산한 낮은 신뢰도의 예상값

프론트는 `crowdLevel`을 기본 표시값으로 사용합니다. 출처가 추가되더라도 enum 파싱 실패로 화면을 중단하지 말고 `NORMAL`로 안전하게 처리합니다.

## 3. 지원 지역명

프론트는 아래 문자열을 그대로 전송합니다.

`서울`, `인천`, `수원·용인`, `가평·양평`, `파주·고양`, `강릉·속초`, `춘천·홍천`,
`평창·정선`, `동해·삼척`, `대전`, `청주`, `충주·제천`, `태안·보령`, `공주·부여`,
`전주`, `군산·익산`, `광주·담양`, `목포·신안`, `경주`, `대구`, `안동`, `포항`,
`부산`, `울산`, `창원`, `통영·거제`, `남해·사천`, `여수`, `해남·완도`, `제주`, `서귀포`.

기존 화면 기본값인 `제주 성산`도 백엔드에서 `제주` 별칭으로 허용합니다.

## 4. 오류 응답

모든 오류는 다음 형식입니다.

```json
{
  "timestamp": "2026-08-30T04:00:00Z",
  "status": 400,
  "code": "BAD_REQUEST",
  "message": "지원하지 않는 여행 지역입니다: 잘못된지역",
  "path": "/api/v1/tour/areas",
  "traceId": "...",
  "details": null
}
```

| HTTP | 의미 | 프론트 처리 |
|---:|---|---|
| `400` | 파라미터 또는 지원 지역 오류 | `message` 표시 |
| `404` | 존재하지 않는 API 경로 | 앱 버전과 API 경로 확인 |
| `405` | 지원하지 않는 HTTP 메서드 | 요청 메서드 확인 |
| `406` | 제공할 수 없는 응답 형식 | `Accept` 헤더 확인 |
| `413` | 요청 데이터 크기 초과 | 전송 데이터 크기 축소 |
| `415` | 지원하지 않는 요청 형식 | `Content-Type` 확인 |
| `429` | 공공 API 호출 한도 초과 | 잠시 후 재시도 안내 |
| `502` | 공공 관광 API 오류 | 재시도 UI 표시 |
| `503` | 키 미설정 또는 일시적인 요청 과다 | 재시도 UI 표시 |

요청값 검증에 실패하면 `details`는 오류 객체 배열입니다. 사용자가 입력한 값은 응답하지 않습니다.

```json
{
  "timestamp": "2026-08-30T04:00:00Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "요청값이 올바르지 않습니다.",
  "path": "/api/v1/trips/1/expenses",
  "traceId": "...",
  "details": [
    {
      "field": "amount",
      "message": "경비 금액이 필수입니다."
    }
  ]
}
```

상세 오류가 없으면 `details`는 `null`입니다. 클라이언트는 `message` 문구가 아니라 `code`를 기준으로 오류를 구분합니다.

## 5. Android 현재 연동 범위

Android 변경은 API 계층과 기존 장소 모델 매핑으로 제한합니다.

- 기존 경로 `/api/v1/tour/areas` 유지
- 기존 장소 필드와 카테고리 분류 유지
- 기존 `regionName`을 API까지 전달
- 응답의 `crowdLevel`만 기존 `PlaceItem.crowdLevel`에 연결
- 화면 Composable, 레이아웃, 내비게이션 변경 없음

여행 시작일을 `targetDate`로 전달하는 작업은 화면 상태 계약 변경이 필요하므로 이번 최소 연동 범위에서는 서버 기본 날짜를 사용합니다.

## 6. AI Agent API

Agent API는 모두 `APP_AI_ENABLED=true`인 서버에서 제공하며 다음 헤더가 필요합니다.

```http
Authorization: Bearer {accessToken}
Content-Type: application/json
```

`accessToken`은 `POST /api/v1/auth/registrations` 또는 `POST /api/v1/auth/tokens`의 응답으로 발급합니다.
요청에 `externalProcessingConsent: true`가 없으면 `400`을 반환합니다.

### 6.1 맞춤 장소 추천 Agent

```http
POST /api/v1/recommendations/places
```

```json
{
  "destination": "서울",
  "regionCode": "11",
  "profile": "조용한 문화 공간과 여유 있는 일정을 좋아합니다.",
  "latitude": 37.5665,
  "longitude": 126.9780,
  "keywords": ["박물관", "실내"],
  "limit": 5,
  "externalProcessingConsent": true
}
```

성공 응답의 `recommendations`에는 `placeId`, `name`, `category`, `score`, `reason`,
`sourcePlaceId`가 들어가고, `reasoning`에는 전체 추천 근거가 들어갑니다.

### 6.2 상황 대처 Agent

```http
POST /api/v1/recommendations/situations
```

장소 추천 요청에 `purpose: "SITUATION_RESPONSE"`와 아래 `situation` 중 필요한 값을 추가합니다.

```json
{
  "congestion": {
    "level": "HIGH",
    "occupancyPercent": 90,
    "area": "서울 도심"
  }
}
```

응답은 `situationSummary`, `routeRecalculationRequired`, `nextAction`,
`placeRecommendations`, `changeProposal`로 구성됩니다.

### 6.3 여행 연계 상황 대처 Agent

```http
POST /api/v1/trips/{tripId}/situation-responses
```

로그인 사용자가 해당 서버 여행의 참여자여야 합니다. 날씨를 생략하면 기상청 초단기실황,
혼잡을 생략하면 관광지 집중률 공공데이터 또는 예상값으로 자동 보강합니다. 진행 중인 여행이면
사용자가 승인할 수 있는 `changeProposal`도 생성합니다.

Agent 공통 오류:

| HTTP | 의미 |
|---:|---|
| `400` | 필수값·범위 오류 또는 외부 처리 미동의 |
| `401` | 서버 로그인 토큰 없음·만료 |
| `403` | 해당 여행의 참여자가 아님 |
| `503` | Agent 비활성화 또는 외부 연동 불가 |

### 6.4 Android 연동 선행 조건

현재 Android 로그인은 로컬 온보딩이며 Gayadi 서버의 JWT를 발급하지 않습니다. 또한 Android 여행 ID는
로컬 문자열이고 서버 여행 ID는 숫자입니다. 따라서 Agent를 화면에서 안전하게 호출하려면 먼저 아래 계약을
연결해야 합니다.

1. Android 로그인 성공 시 서버 `accessToken` 발급·보관
2. Android 여행 생성·조회 시 서버 `tripId` 사용 또는 로컬 ID와 매핑
3. 이후 추천·경로·상황 대처 API에 Bearer 토큰과 서버 `tripId` 전달

Google 로그인은 Android Credential Manager가 발급한 Google ID 토큰을 서버에 넘겨 서버 JWT를 받습니다.

```http
POST /api/v1/auth/google-tokens
Content-Type: application/json

{"idToken":"<Google ID token>"}
```

성공 응답은 `POST /api/v1/auth/tokens`와 같은 `AuthTokenResponse`입니다. 서버의 `GOOGLE_CLIENT_ID`는 Google Cloud의 **웹 클라이언트 ID**여야 하며, Android Credential Manager의 `setServerClientId(WEB_CLIENT_ID)`에도 같은 값을 넣습니다. 서버는 Google 공개키를 캐시하는 공식 Java 검증기로 서명·issuer·audience·만료를 확인합니다. 키가 없으면 `503 AUTH_GOOGLE_NOT_CONFIGURED`입니다.

클라이언트 구현에서는 Credential Manager 호출, ID 토큰 전송, 서버 JWT 저장과 이후 API의 Bearer 헤더 주입을 연결해야 합니다.

인증을 제거하거나 외부 모델 호출 API를 공개하는 방식은 사용하지 않습니다.

## 7. 원시 관광 API

`/locations`, `/keywords`, `/festivals`, `/stays`와 단독 `/api/v1/congestion/forecast`는 내부·관리용이며 JWT 인증이 필요합니다. Android 장소 검색에서는 직접 호출하지 않습니다.
