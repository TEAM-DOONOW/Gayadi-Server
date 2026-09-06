# Refresh Token Rotation 설계

## 목적

Access Token의 유효시간을 짧게 운영하면서 재로그인을 줄이기 위해 Refresh Token Rotation(RTR)을 적용한다. 탈취된 Refresh Token이 재사용되면 동일 로그인 세션 전체를 폐기한다.

## Android 전달 기준

- 로그인 성공 시 Access Token과 Refresh Token을 JSON으로 반환한다.
- Android는 Refresh Token을 Keystore 기반 암호화 저장소에 보관한다.
- 토큰은 로그, 분석 이벤트, crash report, clipboard와 일반 백업에 남기지 않는다.
- 서버는 Refresh Token 원문을 DB나 Redis에 저장하지 않는다.

## 수명 정책

| 항목 | 기본값 | 의미 |
|---|---:|---|
| Access Token | 15분 | API 호출에만 사용하며 메모리에 유지한다. |
| Refresh idle timeout | 30일 | 마지막 정상 갱신 이후 사용하지 않은 세션을 종료한다. |
| Refresh absolute lifetime | 90일 | 계속 사용하더라도 다시 로그인해야 하는 최대 기간이다. |

회전할 때 idle timeout은 다시 계산하지만 absolute lifetime은 늘리지 않는다. 여러 기기에서 로그인하면 기기마다 독립된 session과 token family를 생성한다. Access Token 15분 전환은 RTR 서버와 Android 연동이 완료된 뒤 함께 배포한다.

## API 계약

| API | 역할 |
|---|---|
| `POST /api/v1/auth/tokens` | 최초 토큰 쌍 발급 |
| `POST /api/v1/auth/google-tokens` | Google 로그인 후 토큰 쌍 발급 |
| `POST /api/v1/auth/token-refreshes` | 기존 Refresh Token을 소비하고 새 토큰 쌍 발급 |
| `DELETE /api/v1/auth/sessions/current` | 현재 Refresh 세션 폐기 |
| `DELETE /api/v1/auth/sessions` | 계정의 모든 Refresh 세션 폐기 |

## Redis 저장 정보

Refresh Token은 256bit 이상의 난수로 생성하는 opaque token이다. Redis에는 토큰 해시, 사용자 ID, 세션 ID, token family ID, 발급·만료 시각과 사용 여부만 TTL과 함께 저장한다. 원문으로 Redis key를 만들지 않는다.

## 원자적 갱신과 재사용 탐지

Lua script 한 번으로 기존 토큰 검증, 사용 완료 전환과 새 토큰 저장을 처리한다. 동일 토큰의 동시 요청은 하나만 성공해야 한다. 이미 사용된 토큰이 들어오면 재사용으로 판단해 token family 전체를 폐기한다. Redis 장애나 script 실패 시 갱신은 fail-closed로 거부한다.

## 폐기 범위

- 로그아웃: 현재 세션 폐기
- 토큰 재사용: 해당 token family 전체 폐기
- 비밀번호 변경·탈퇴·계정 정지: 사용자의 모든 세션 폐기

Access Token은 자체 만료 전까지 남을 수 있다. RTR 다음 단계에서 Access Token TTL을 단축하고 필요하면 JTI denylist를 추가한다.

## 자동 로그인 흐름

1. 앱 시작 시 Access Token이 유효하면 그대로 사용한다.
2. Access Token이 없거나 만료됐으면 저장한 Refresh Token으로 한 번 갱신한다.
3. 여러 API가 동시에 401을 받아도 앱은 mutex로 갱신 요청 하나만 보낸다.
4. 성공하면 새 토큰 쌍을 먼저 안전하게 저장한 뒤 대기 중인 API를 한 번만 재시도한다.
5. 네트워크 장애와 5xx에서는 토큰을 삭제하거나 로그아웃하지 않는다.
6. 갱신이 401로 거부되거나 재사용이 탐지된 경우에만 인증정보를 삭제하고 로그인 화면으로 이동한다.

앱 재시작 후에는 Refresh Token만 복원한다. Access Token은 필요할 때 새로 발급하며 로그와 일반 SharedPreferences에는 저장하지 않는다.

## 모바일 위협 대응

- Android 앱은 client secret을 숨길 수 없는 public client로 취급한다.
- 앱 바이너리에 공통 암호키나 API 인증 secret을 포함하지 않는다.
- Refresh Token 암호화 키는 Android Keystore에서 만들고 가능한 경우 hardware-backed key를 사용한다.
- 루팅·디버깅 탐지는 보조 신호로만 사용한다.
- 위험도가 커지면 Play Integrity와 DPoP 기반 sender-constrained token을 별도 검토한다.

## 구현 순서

1. Refresh 설정과 오류 코드
2. opaque token 생성·해시와 응답 DTO 확장
3. Redis Lua 기반 발급·회전·family 폐기
4. 갱신·로그아웃 API
5. 이메일·Google 로그인에 최초 세션 발급 연결
6. 로그아웃·재사용·동시 갱신·Redis 장애 테스트
7. Android 연동 계약과 보안 문서 최신화

## 완료 기준

- 토큰 원문이 서버 저장소와 로그에 남지 않는다.
- 동일 토큰 동시 갱신 중 하나만 성공한다.
- 사용된 토큰 재사용 시 family 전체가 폐기된다.
- Redis 장애 시 갱신이 허용되지 않는다.
- 로그아웃 이후 해당 세션으로 갱신할 수 없다.
- H2 전체 테스트와 실제 Redis 통합 테스트가 통과한다.

## 배포 환경 검증 체크리스트

Java 계층의 발급·회전·재사용·로그아웃·Redis 장애 처리는 단위 테스트로 검증한다. 다음 항목은 실제 Redis가 연결된 배포 환경에서 최종 확인한다.

- 동일 Refresh Token으로 동시에 갱신했을 때 요청 하나만 성공한다.
- 성공한 회전 이후 기존 토큰은 사용 완료 상태이며 새 토큰에는 올바른 TTL이 설정된다.
- 사용 완료 토큰을 다시 제출하면 동일 token family가 폐기된다.
- 로그아웃 이후 같은 세션의 Refresh Token은 갱신에 사용할 수 없다.
- Redis 연결 중단 시 토큰 발급·갱신·로그아웃이 fail-closed로 거부된다.
- 응답과 로그에 Refresh Token 원문과 Redis 내부 key가 남지 않는다.

## 배포 환경 검증 체크리스트

현재 Java 계층의 발급·회전·재사용·로그아웃·Redis 장애 처리는 단위 테스트로 검증한다. 다음 항목은 실제 Redis가 연결된 배포 환경에서 최종 확인한다.

- 동일 Refresh Token으로 동시에 갱신했을 때 요청 하나만 성공한다.
- 성공한 회전 이후 기존 토큰은 사용 완료 상태이며 새 토큰에는 올바른 TTL이 설정된다.
- 사용 완료 토큰을 다시 제출하면 동일 token family가 폐기된다.
- 로그아웃 이후 같은 세션의 Refresh Token은 갱신에 사용할 수 없다.
- Redis 연결 중단 시 토큰 발급·갱신·로그아웃이 fail-closed로 거부된다.
- 응답과 애플리케이션 로그에 Refresh Token 원문과 Redis 내부 key가 남지 않는다.
