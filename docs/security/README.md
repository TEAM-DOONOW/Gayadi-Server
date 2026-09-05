# GAYADI 보안 매뉴얼

이 디렉터리는 GAYADI Android API를 개발·배포·운영할 때 지켜야 하는 보안 기준을 설명한다. 새 기능은 이 문서의 규칙을 기본값으로 적용하고, 예외가 필요하면 이유와 대체 통제를 함께 기록한다.

## 처음 읽는 순서

1. [현재 보안 구조](security-overview.md)에서 요청과 인증 처리 흐름을 이해한다.
2. API를 개발할 때 [권한과 IDOR/BOLA](authorization-matrix.md), [입력과 링크](xss-input-policy.md), [민감정보 보호](sensitive-data-protection.md)를 적용한다.
3. 인증 기능은 [JWT](jwt-security.md), [Refresh Token](refresh-token-rotation.md), [Rate Limit](auth-rate-limit.md)을 따른다.
4. 배포 전에는 [CI 보안](ci-security.md)과 [운영 체크리스트](operations-security-checklist.md)를 확인한다.
5. 개인정보 저장 구조를 변경할 때는 [DB 개인정보 보호](database-data-protection.md)를 먼저 검토한다.

| 문서 | 내용 |
|---|---|
| [현재 보안 구조](security-overview.md) | HTTP 경계, JWT, 입력·로그 보호의 실제 동작 |
| [민감정보 보호](sensitive-data-protection.md) | 로그 마스킹과 DB 저장 시 보호 기준 |
| [Android 입력과 링크 보안](xss-input-policy.md) | Android 텍스트, 외부 링크와 향후 WebView 처리 기준 |
| [JWT 보안](jwt-security.md) | Access Token claim, 검증 조건과 Redis·RTR 전환 기준 |
| [Redis 보안 기반](redis-security.md) | 연결 설정, key namespace, TTL과 장애 처리 기준 |
| [Refresh Token Rotation 설계](refresh-token-rotation.md) | 토큰 전달, 원자적 회전과 재사용 대응 기준 |
| [인증 API Rate Limit](auth-rate-limit.md) | 인증 API별 요청 한도와 Redis 장애 정책 |
| [API 권한과 IDOR/BOLA](authorization-matrix.md) | 도메인별 접근 권한과 객체 ID 공격 검증 기준 |
| [DB 개인정보 보호](database-data-protection.md) | 필드 분류, 검색 가능한 암호화 전환과 키 관리 기준 |
| [운영 보안 체크리스트](operations-security-checklist.md) | 배포 전후 검증과 사고 대응 순서 |
| [CI 보안 검사](ci-security.md) | 빌드·CodeQL·의존성·secret·이미지 보안 게이트 |
| [Android 기기와 로그인 세션](device-session-policy.md) | 기기 등록, 세션 저장소, 기기 대수 제한과 로그아웃 기준 |
| [보안 강화 로드맵](security-hardening-roadmap.md) | 전역 보안 정책, JWT·Redis·RTR와 DB 암호화 구현 순서 |

## 개발자가 반드시 지킬 규칙

- 사용자 식별자는 요청값이 아니라 인증된 JWT의 `sub`를 사용한다.
- 경로의 자원 ID마다 소유자·참여자 권한을 Service에서 다시 확인한다.
- 비밀번호와 토큰 원문, 외부 credential을 DB·로그·오류 응답에 남기지 않는다.
- 사용자 입력은 DTO에서 길이·형식·목록 크기를 제한하고 SQL parameter binding을 사용한다.
- 외부 호출과 비용이 큰 API에는 용도별 Rate Limit과 timeout을 적용한다.
- 새 보안 오류는 공통 API 오류 형식과 한국어·영어 메시지를 함께 제공한다.
- 보안 정책 변경에는 정상·거부·장애 시나리오 테스트를 추가한다.

## 현재 운영 전 확인이 필요한 항목

- 실제 Redis의 RTR 동시성·TTL과 다중 서버 Rate Limit
- Reverse proxy 실제 IP 정규화, HTTPS와 운영 CORS
- Android Keystore 저장과 자동 로그인
- KMS 준비 후 이메일 암호화·검색 index 전환
- GitHub Ruleset, 필수 검사와 Secret Push Protection
- 암호화된 DB 백업 복구 훈련

구현 현황과 다음 작업 범위는 [보안 강화 로드맵](security-hardening-roadmap.md), 실행 방법은 [운영 보안 체크리스트](operations-security-checklist.md)를 따른다.
