# Android 기능과 서버 API 대응표

이 문서는 `Gayadi-Android`의 화면·도메인 모델을 기준으로 서버가 제공해야 하는 계약과 실제 연결 상태를 기록한다. 서버 구현 완료와 Android 앱의 원격 저장소 연결 완료를 구분한다.

## 기능 대응

| Android 사용자 기능 | 서버 도메인 | 대표 API | 서버 상태 | 현재 Android 데이터 원본 |
| --- | --- | --- | --- | --- |
| 가입·로그인·프로필·탈퇴 | `auth` | `/api/v1/auth/**`, `/api/v1/users/current` | 구현(이메일·Google ID 토큰) | 로컬 프로필, 로그인 화면은 서버 미연결 |
| 여행 성향 설문·결과 | `survey` | `/api/v1/surveys/**` | 구현 | Firestore |
| 여행 생성·편집·상태 변경 | `travel` | `/api/v1/trips/**` | 구현 | 단말 파일 |
| 초대 코드 참여·참여자 관리 | `invitation`, `travel` | `/api/v1/trip-memberships`, `/trips/{tripId}/invitations`, `/participants` | 구현 | 단말 파일 + Firestore |
| 그룹 가능 날짜 제출·확정 | `coordination` | `/trips/{tripId}/date-coordination/**` | 구현 | Firestore |
| 일정 CRUD·정렬·방문 처리 | `schedule` | `/trips/{tripId}/schedules`, `/schedule-orders` | 구현 | 단말 파일 |
| 장소 검색·상세·주변·찜 | `place`, `favorite`, `tourapi` | `/api/v1/places`, `/api/v1/tour/**`, `/favorite-places` | 구현 | TourAPI만 서버 호출, 찜은 단말 파일 |
| 출발·귀가 경로 추천·선택 | `route` | `/route-recommendations`, `/route-selections` | 구현 | 단말 파일 |
| 경비 CRUD·공금·정산 | `expense` | `/expenses`, `/shared-fund`, `/expense-settlement` | 구현 | 단말 파일 |
| 여행 홈 집계 | `dashboard` | `/trips/{tripId}/dashboard` | 구현 | 단말 파일 조합 |
| 날씨·혼잡·교통 변화 대응 | `weather`, `event`, `recommendation` | `/situation-responses`, `/change-proposals`, `/weather/**` | 구현 | 재일정 UI 상태만 로컬이며 화면/API 연결 없음 |
| 공지·약관·문의 | `notice`, `legal`, `support` | `/notices`, `/legal-documents`, `/inquiries` | 구현 | Firestore |

## Agent 판단

서버 Agent는 화면이 필요로 하는 “현재 여행에서 변수 발생 → 대안 제시 → 사용자 승인/거절 → 일정과 경로 갱신” 경계를 갖는다.

1. Android는 여행 ID와 상황만 `POST /api/v1/trips/{tripId}/situation-responses`로 보낸다.
2. 서버가 여행 지역, 참여자, 성향, 일정, 저장 장소를 조회하고 날씨가 없으면 기상청 실황을 보충한다.
3. TourAPI 후보 검색과 AI 판단 뒤, 서버가 지역·실내 여부 같은 하드 제약을 다시 검증한다.
4. 진행 중 여행에는 일정 revision을 고정한 변경안을 저장한다.
5. 사용자가 `/change-proposals/{proposalId}`를 승인하면 미래 일정만 바꾸고 기존 경로를 만료시킨다.
6. 앱은 `/dashboard`를 다시 조회해 승인 결과를 반영한다.

이 경로는 `AiUserJourneyIntegrationTests`와 `ApiFirstAgentSystemTests`가 외부 모델 없이 재현 가능하게 검증한다. 실제 Groq·TourAPI·기상청 호출은 선택 어댑터이므로 키가 없거나 공급자가 실패해도 기존 여행과 확정 일정 조회는 유지된다.

## 연결 시 주의점

- Android의 `TravelRepository`는 여러 도메인을 하나의 `TravelState` 파일에 저장한다. 서버 연결 시 이를 그대로 하나의 거대한 API 저장소로 옮기지 말고 인증, 여행, 날짜 조율, 일정, 경비, 경로별 원격 저장소로 나눈다.
- 서버 식별자는 `long`, Android 모델 식별자는 `String`이다. 네트워크 DTO에서 문자열 변환을 담당하고 도메인 로직에 파싱을 흩뿌리지 않는다.
- 앱 날짜 표시값 `yyyy.MM.dd`와 ISO `yyyy-MM-dd`를 서버가 모두 받고, 응답 표시 형식은 `yyyy.MM.dd`, 시간은 `HH:mm`으로 통일한다.
- 일정 변경 승인은 반드시 서버의 `baseRevisionNo` 충돌 검사를 거친다. Android의 로컬 `RescheduleDecision`만 바꾸면 실제 일정은 변경되지 않는다.
- 영수증의 `content://` URI는 다른 장치나 서버에서 읽을 수 없다. 운영 연결 시 별도 업로드 API와 서버 URL 계약이 필요하다.
