# Android 기기와 로그인 세션 정책

## 결론

기기 등록과 로그인 세션은 같은 개념이 아니다. `user_devices`는 앱 설치 단위 정보를 PostgreSQL에 보관하고, 별도 인증 세션은 기기와 연결한다. Redis는 Refresh Token의 실시간 검증·회전·폐기에 사용한다. Access Token 블랙리스트보다 기기별 Refresh 세션 관리와 RTR 완성을 먼저 진행한다.

> 구현 상태: 후속 기능 개발 TODO. 현재 전역 보안 강화 범위에는 포함하지 않는다.

## 현재 상태

Flyway V1의 `user_devices`에는 `user_id`, `device_id`, 기기명, 플랫폼, 푸시 설정, 상태와 최근 활동 시각이 있다. 현재 이 테이블을 사용하는 등록 API와 Repository는 없다. 이 테이블만으로는 로그인 이력, token family, 세션 만료와 폐기 사유를 충분히 표현할 수 없다.

## 식별자 기준

- 앱 최초 실행 시 임의 UUID인 `installationId`를 생성한다.
- Keystore 기반 키로 보호되는 앱 내부 저장소에 보관한다.
- IMEI, 전화번호, 광고 ID, MAC 주소 등 영구 하드웨어 식별자는 수집하지 않는다.
- 앱 삭제 후 재설치는 새 설치로 취급한다.
- `installationId`는 인증 수단이 아니라 기기 구분 값이다.

## 저장소 역할

### PostgreSQL

- 앱 설치 정보와 사용자에게 표시할 기기명
- 활성 세션 수 제한의 최종 기준
- 로그인·로그아웃·강제 종료 시각과 폐기 사유
- 사용자 전체 로그아웃과 보안 감사 기록

### Redis

- Refresh Token 해시와 상태
- 세션·token family 폐기 표시
- 원자적 Refresh Token 회전
- 사용자별 활성 session ID index
- 인증 요청 rate limit

Redis 장애 시 Refresh 갱신은 fail-closed로 거부한다. Redis 데이터가 유실돼도 DB의 기기·세션 소유 관계와 감사 기록은 유지돼야 한다.

## 권장 DB 구조

기존 `user_devices`를 유지하고 `user_auth_sessions`를 추가한다.

```text
user_devices
  id, user_id, device_id, device_name, platform,
  push_enabled, status, last_active_at, created_at, updated_at

user_auth_sessions
  id, user_id, user_device_id, session_id, token_family_id,
  status, issued_at, last_refreshed_at, idle_expires_at,
  absolute_expires_at, revoked_at, revoke_reason
```

Refresh Token 원문과 해시는 DB에 저장하지 않는다. 세션 목록 API에도 토큰, family ID와 Redis key를 반환하지 않는다.

## 기기 대수 제한

기기 제한은 필수 보안 요건이 아니라 제품 정책이다. 여행 소비자 앱은 사용자당 활성 로그인 세션 5개를 기본값으로 권장한다.

- 전체 기기 행이 아니라 `ACTIVE` 인증 세션 수를 제한한다.
- 같은 `installationId`의 재로그인은 기존 기기를 갱신한다.
- 한 기기에 사용자당 활성 Refresh 세션 하나만 유지한다.
- 5개 초과 시 가장 오래 활동하지 않은 세션을 종료한다.
- 사용자는 기기 관리 화면에서 종료된 기기를 확인할 수 있어야 한다.
- 동시 로그인은 사용자 행 잠금 등으로 직렬화해 제한을 우회하지 못하게 한다.

## API 제안

| API | 역할 |
|---|---|
| `POST /api/v1/auth/tokens` | `installationId`와 기기명을 받아 로그인 |
| `POST /api/v1/auth/google-tokens` | Google 로그인과 기기 등록 |
| `GET /api/v1/auth/sessions` | 내 활성 기기·세션 목록 |
| `DELETE /api/v1/auth/sessions/current` | 현재 기기 로그아웃 |
| `DELETE /api/v1/auth/sessions/{sessionId}` | 선택한 기기 로그아웃 |
| `DELETE /api/v1/auth/sessions` | 모든 기기 로그아웃 |

## Access Token 블랙리스트

기본 정책에서는 매 API 요청마다 Redis를 조회하는 Access Token 블랙리스트를 사용하지 않는다. RTR 완료 후 Access Token을 15분으로 줄이고 Refresh 세션만 즉시 폐기한다.

관리자 즉시 차단, 계정 탈취 대응 또는 고위험 기능 때문에 15분도 허용할 수 없을 때만 남은 TTL 동안 `jti` denylist를 추가한다.

## 구현 순서

1. RTR 발급·회전·현재 세션 폐기 테스트 완성
2. `user_devices` Repository와 등록·갱신 구현
3. `user_auth_sessions` migration과 Repository 구현
4. 로그인 요청에 `installationId` 연결
5. 활성 세션 5개 제한과 오래된 세션 자동 종료
6. 세션 목록·선택 로그아웃·전체 로그아웃 API
7. 비밀번호 변경·탈퇴·정지와 전체 세션 폐기 연결
8. Access Token 15분 전환
9. 위험도 재평가 후 JTI denylist 여부 결정

## 검증 기준

- 같은 기기의 재로그인이 기기 수를 늘리지 않는다.
- 동시 로그인에도 활성 세션이 제한을 초과하지 않는다.
- 선택·전체 로그아웃 후 해당 Refresh Token을 사용할 수 없다.
- 다른 사용자의 session ID로 로그아웃할 수 없다.
- 앱 재설치는 새 설치로 처리된다.
- DB와 Redis 부분 실패 시 세션 상태를 복구할 수 있다.
- 응답과 로그에 Refresh Token 원문과 내부 Redis key가 노출되지 않는다.
