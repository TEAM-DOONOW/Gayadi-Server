# 도메인 오류 코드

도메인 ErrorCode는 해당 기능 패키지에 위치한다.

| 영역 | ErrorCode |
|---|---|
| 공통 HTTP·Validation·Security | `CommonErrorCode` |
| 인증·사용자 | `AuthErrorCode`, `UserErrorCode` |
| 여행·일정·경로 | `TripErrorCode`, `ScheduleErrorCode`, `RouteErrorCode` |
| 일정 조율·현장 상황 | `CoordinationErrorCode`, `EventErrorCode` |
| 경비·찜·친구·초대 | `ExpenseErrorCode`, `FavoriteErrorCode`, `FriendshipErrorCode`, `InvitationErrorCode` |
| 장소·설문·혼잡도 | `PlaceErrorCode`, `SurveyErrorCode`, `CongestionErrorCode` |
| 추천·관광·날씨 | `RecommendationErrorCode`, `TourApiErrorCode`, `WeatherErrorCode` |
| 공지·법적 문서 | `NoticeErrorCode`, `LegalErrorCode` |

## 외부 연동 코드

- 설정 또는 API 키 없음: 503
- 외부 인증 실패: 503
- 외부 요청 제한: 429
- 호출 실패·중단·잘못된 응답: 502
- 정상 응답이지만 데이터 없음: 도메인 정책에 따라 404 또는 409

제공자 응답 코드와 메시지는 로그 진단에만 사용하며 API 응답 계약으로 노출하지 않는다.
