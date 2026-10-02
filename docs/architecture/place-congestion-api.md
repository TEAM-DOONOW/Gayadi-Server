# 장소별 혼잡도 상세 API

관광 목록의 장소 화면은 가야디 장소 번호가 아니라 지역 코드와 좌표로 조회합니다.

```http
GET /api/v1/congestion/forecast?areaCode=11&districtCode=110&hours=9,11,13,15,17,19&lat=37.5796&lon=126.9770
```

응답에는 일별 `level`/`concentrationScore`, 시간대 `points`, 좌표를 보낸 경우 `weather`가 함께 있습니다.

가야디에 저장된 장소는 다음 API로 장소 정보, 현재 날씨, 현재 혼잡도와 시간대별 혼잡도를 조회합니다. `placeId`는 관광공사 `contentId`가 아닙니다.

```http
GET /api/v1/congestion/places/{placeId}?hours=9,11,13,15,17,19
```

`hours`를 생략하면 `9, 11, 13, 15, 17, 19시`를 사용합니다. 외부 공급자 일부가 실패해도 조회 가능한 나머지 정보는 반환합니다.

API 경로·조합 서비스·최종 응답 DTO는 장소별 혼잡도 유스케이스이므로 `congestion` 도메인에 둡니다. 장소 기본 정보는 `place`, 기상청 연동과 날씨 응답 DTO는 `weather`가 소유하며 `PlaceCongestionDetailResponse`가 각 도메인 응답을 조합합니다.

## 데이터 공급 순서

1. **서울시 실시간 인구**: 서울시 지정 핫스팟에 해당하면 현재 추정 인구와 공급자 시간대 예측을 사용합니다. 무료이지만 서울 주요 121개 장소만 지원합니다.
2. **TMAP Puzzle**: 서울시 데이터를 사용할 수 없고 지원 POI가 일치하면 전국 실시간 밀도와 최근 30일 동일 요일 시간대 통계를 사용합니다. 유료 호출 가능성이 있어 기본 비활성화합니다.
3. **한국관광공사/달력 기반 예측**: 위 공급자를 사용할 수 없는 전국 장소의 대체값입니다. 실시간 측정값이 아니므로 응답에 `FORECAST`로 표시합니다.

응답의 `source`, `dataType`, `hourlySource`, `hourlyDataType`을 통해 앱이 실시간·통계·추정 데이터를 구분할 수 있습니다. 서로 다른 공급자의 수치를 같은 의미의 절대 점수로 취급하지 않습니다.

## 날씨

- 현재 기온: 기상청 초단기실황
- 하늘 상태와 강수확률: 현재 시각에 가장 가까운 초단기예보
- 좌표를 둘 다 보내지 않으면 `weather`는 없습니다. 실황과 초단기예보가 모두 실패하면 `weather.available=false`입니다. 한쪽만 성공하면 그 값으로 요약을 만듭니다.

기상청 단기예보 조회서비스 키를 다음 설정으로 연결합니다. 인코딩 키와 디코딩 키를 모두 받습니다.

```yaml
weather:
  api:
    key: ${WEATHER_API_KEY:}
    base-url: ${WEATHER_API_BASE_URL:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}
```

운영 환경에서는 `weather_api_key` Docker secret을 우선 사용하며, 실제 키는 저장소에 기록하지 않습니다.

## 설정

```yaml
congestion:
  tmap:
    enabled: false
    app-key: ""
  seoul:
    enabled: false
    api-key: ""
    base-url: ""
```

서울 열린데이터광장의 공식 예시 주소는 HTTP입니다. API 키가 URL 경로에 포함되므로 운영에서는 HTTPS를 제공하는 내부 프록시 주소를 `CONGESTION_SEOUL_BASE_URL`로 설정한 경우에만 활성화합니다.

## 네이버·카카오 지도 데이터

지도 앱 화면에 표시되는 인기 시간대나 혼잡도는 네이버·카카오의 공개 장소 API 응답 항목이 아닙니다. 비공개 API 역공학이나 화면 크롤링은 약관, 차단, 응답 변경 위험이 있으므로 사용하지 않습니다. 네이버 Directions의 혼잡도는 장소 인파가 아니라 도로 속도 기반 교통 혼잡도입니다.

## 제한 사항

- TMAP과 서울시 모두 지원 장소 목록에 없는 관광지는 실시간 값이 제공되지 않습니다.
- 서울시 데이터는 통신 신호를 가공한 추정 인구이므로 실제 현장 인원과 차이가 날 수 있습니다.
- 시간대별 `score`는 화면 비교를 위한 상대값입니다. 원본 밀도 또는 예상 인구는 별도 필드로 함께 제공합니다.
