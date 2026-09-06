# 보안 적용 현황과 후속 작업

## 문서 목적

이 문서는 저장소에 적용된 보안 기능과 외부 환경에서 완료해야 하는 후속 작업을 구분한다. GAYADI는 Android 중심 JSON REST API이다.

보안 기능은 설계 검토 → 테스트 → 구현 → H2·PostgreSQL·외부 연동 검증 → 문서 갱신 순으로 적용한다.

## 1. Redis 운영 연결 검증 — 완료

- 현재 준비된 [Redis 보안 저장소](redis-security.md)를 개발·운영 환경별 인증·TLS·timeout 설정으로 검증한다.
- 운영 환경의 key prefix가 개발·테스트와 충돌하지 않는지 확인한다.
- Refresh 세션은 Redis 장애 시 fail-closed, 일반 rate limit은 위협과 가용성을 따로 검토한다.
- 원자적 갱신·폐기는 Lua script 또는 Redis transaction으로 구현한다.
- Testcontainers Redis 통합 테스트로 TTL·장애·동시성을 검증한다.

## 2. Refresh Token Rotation — 서버 구현 완료, 실제 Redis 검증 대기

상세 구현 계약은 [Refresh Token Rotation 설계](refresh-token-rotation.md)를 따른다.

- Access Token은 짧은 JWT, Refresh Token은 일회성 opaque token으로 분리한다.
- Redis에는 Refresh Token 원문 대신 해시, 사용자 ID, 세션 ID, token family ID와 만료만 저장한다.
- 갱신 시 기존 Refresh Token을 한 번만 소비하고 새 토큰으로 원자적 교체한다.
- 이미 소비된 토큰이 재사용되면 해당 token family 전체를 폐기한다.
- 로그아웃·비밀번호 변경·탈퇴 시 해당 범위의 세션을 폐기한다.
- Android는 Refresh Token을 Keystore 기반 안전한 저장소에 보관하고 로그·clipboard·backup에 남기지 않는다.
- RTR 도입 후 Access Token TTL을 짧게 조정한다.

발급·회전·재사용 탐지·현재 세션 폐기와 Redis 장애 fail-closed 경로는 구현 및 단위 테스트를 완료했다. 실제 Redis의 동시 갱신 원자성과 TTL은 배포 환경 체크리스트에서 확인한다.

## 3. 인가와 남용 방지 — 인증 API 및 핵심 자원 기준 완료

- 도메인별 소유자·참여자·관리자 권한표를 작성하고 IDOR/BOLA 테스트를 추가한다.
- Controller의 인증 경계와 Service의 자원 단위 권한 검사를 함께 유지한다.
- 로그인·가입·Google 로그인·토큰 갱신·초대 코드·AI·외부 API에 다른 rate limit을 적용한다.
- 신뢰할 proxy를 통과한 클라이언트 IP만 rate limit 기준으로 사용한다.
- 무차별 대입·토큰 재사용·권한 거부 급증을 민감정보 없는 구조화 이벤트로 남긴다.

현재 회원가입·로그인·토큰 갱신·초대·AI 추천·관리 API에 Redis Rate Limit을 적용했다. 도메인별 권한표를 작성하고 여행·일정·경비·대시보드의 비참여자 ID 직접 대입을 통합 테스트한다. 차단과 Redis 장애는 토큰·개인정보 없는 구조화 보안 이벤트로 기록한다.

## 4. DB 개인정보 보호 — 분류 및 안전한 전환 기준 완료

수집하지 않아도 되는 데이터를 먼저 제거한다. 비밀번호는 복호화 가능한 암호화가 아니라 BCrypt·Argon2id 단방향 해시를 사용한다.

| 데이터 | 처리 기준 |
|---|---|
| Refresh·재설정·인증 토큰 | 원문 저장 금지, 해시와 TTL |
| 이메일 등 정확 검색 정보 | 필요성 검토 후 AES-GCM 암호문과 별도 HMAC 검색 index |
| 전화번호·상세 주소 | AES-256-GCM envelope encryption |
| 주민등록번호·정부 식별자 | 원칙적으로 수집·저장하지 않음 |
| 외부 API credential | DB·Git 저장 금지, Secret Manager·KMS 사용 |

암호화를 도입하기 전에 필드별 수집 목적, 조회 방식, 보존·파기, 복호화 주체를 확정한다. 키는 코드·Git·DB와 분리하고 key version·nonce·ciphertext·authentication tag를 관리한다.

필드별 처리표와 검색용 HMAC index, AEAD 이중 쓰기·백필·전환 순서는 [DB 개인정보 보호 기준](database-data-protection.md)에 정의했다. 실제 컬럼 암호화는 KMS와 복구 절차가 준비된 배포 작업이며, 공통키를 코드에 넣는 임시 구현은 허용하지 않는다.

## 5. 운영 보안 — 저장소 기반 완료, 인프라 검증 대기

- CI에 의존성 취약점, secret, SAST와 container image 검사를 추가한다.
- 보안 헤더·CORS·인증·인가·rate limit·RTR·로그 마스킹 회귀 테스트를 유지한다.
- 감사 로그의 접근 권한, 보존 기간과 위변조 방지를 정한다.
- 백업 암호화, 복구 훈련, 키 교체와 사고 대응 절차를 검증한다.
- 운영 HTTPS·reverse proxy·CORS 설정을 배포 전 체크리스트로 확인한다.

CI에 빌드, CodeQL, 의존성 변경 검사, Git 이력 secret 검사를 활성화했고 배포 이미지의 HIGH·CRITICAL 취약점을 차단한다. 운영 환경에서만 가능한 항목은 [운영 보안 체크리스트](operations-security-checklist.md)로 관리한다.

## 적용 상태 요약

| 영역 | 저장소 상태 | 외부 완료 조건 |
|---|---|---|
| Redis·RTR | 발급·회전·재사용·로그아웃 구현 | 실제 Redis 동시성·TTL 검증 |
| JWT | 15분 TTL, `kid`, 현재·이전 키 검증 | 실제 키 교체 훈련 |
| 남용 방지 | 인증·초대·AI·관리 API Rate Limit | proxy IP와 다중 서버 검증 |
| 인가 | 권한표와 핵심 IDOR/BOLA 테스트 | 새 도메인마다 회귀 테스트 확대 |
| 개인정보 | 분류와 AEAD/HMAC 전환 기준 | KMS 준비 후 이중 쓰기·백필·전환 |
| CI·운영 | 빌드·CodeQL·의존성·secret·이미지 검사 | Ruleset·HTTPS·백업 복구 검증 |

저장소 구현과 자동 테스트가 끝났다는 사실은 운영 검증 완료를 뜻하지 않는다. Redis 원자성·TTL, proxy 주소 처리, Android 자동 로그인, KMS 암호화와 백업 복구는 해당 환경에서 증빙을 남긴 뒤 완료로 판단한다.

Android 기기 등록과 기기별 로그인 세션 관리는 전역 보안 설정 완료 후 진행하는 별도 기능 개발이다. 현재 보안 강화 작업의 선행 조건으로 취급하지 않는다.

후속 작업은 실제 Redis 검증 → proxy·HTTPS·CORS → Android 자동 로그인 → KMS DB 전환 → 백업 복구 훈련 순서를 권장한다.

## 참고 자료

- [Spring Security HTTP Response Headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
- [OWASP Key Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Key_Management_Cheat_Sheet.html)
