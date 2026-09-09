# 본인 참여자 장소 설정

`PUT /api/v1/trips/{tripId}/participants/current/settings`

인증된 JOINED 참여자의 출발/귀가 장소를 변경합니다. 기존 참여자 추가 API와 별도이며, 참여자 ID와 역할을 유지합니다.

```json
{"departurePlaceId": 1, "returnPlaceId": 2}
```

두 필드를 전체 교체하며 null 또는 생략한 필드는 해제합니다. 하나만 변경하는 클라이언트는 참여자 조회에서 다른 값을 유지해 전송해야 합니다. 다른 사용자의 설정을 변경할 수 없습니다. 접근 불가능하거나 존재하지 않는 장소는 거부합니다.

값이 변경되면 본인의 추천/선택 개인 경로를 만료합니다. 그룹 여행 동선은 유지하며, 동일 값 재전송은 경로를 만료하지 않습니다. 여행 잠금과 트랜잭션으로 설정 변경 및 경로 만료를 함께 처리합니다.

## 검증

- `./gradlew test bootJar --console=plain`: 157 tests, failures/errors 0, 빌드 성공.
- HTTP: 인증 없음 401, 미참여자 403, 가입한 일반 참여자 본인 변경, 주최자 설정 유지.
- 계약: 참여자 ID/역할 유지, 개인 경로 만료, 그룹 경로 유지, null 해제, 접근 불가 장소 거부.

Android 이슈 TEAM-DOONOW/Gayadi-Android#121에서 이 API를 사용합니다. dev 서버 배포 후 Android `DevPlanningIntegrationTest`를 `liveApi=true`, `participantSettings=true`로 실행해 개인 출발/귀가 경로까지 검증해야 합니다. 이번 변경에서는 서버 배포를 수행하지 않았습니다.
