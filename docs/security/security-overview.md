# GAYADI 보안 구조

## 적용 범위

GAYADI는 Android 앱을 주 클라이언트로 사용하는 Stateless JSON REST API다. 현재 HTTP 요청 경계, Bearer Access Token, 공통 401·403 응답, 입력 상한과 로그 마스킹을 적용한다.

이 문서의 “현재 적용”은 코드와 자동 테스트로 확인된 범위를 뜻한다. Redis·RTR, 전체 인가 감사, DB 필드 암호화와 운영 모니터링은 아직 적용된 기능이 아니다.

## 요청 처리 흐름

```text
Android·Swagger 요청
    ↓
CORS 및 HTTP 보안 헤더
    ↓
SecurityFilterChain의 공개·인증 API 구분
    ↓
JwtAuthenticationFilter의 Bearer Token 검증
    ↓
사용자 활성 상태 확인 및 SecurityContext 구성
    ↓
Controller DTO Validation
    ↓
Service 인가·업무 규칙
    ↓
Repository 바인딩 SQL
```

## 전역 HTTP 보안

[`SecurityConfig`](../../src/main/java/com/gayadi/server/config/security/SecurityConfig.java)가 다음을 관리한다.

- 세션을 생성하지 않는 `STATELESS` 정책
- Authorization Header Bearer Token 사용 전제의 CSRF 비활성화
- 공개 API allowlist와 나머지 API 인증 강제
- `X-Content-Type-Options: nosniff`
- frame 사용 금지와 `Referrer-Policy: no-referrer`
- camera·microphone·geolocation·payment 비활성화
- CSP Report-Only와 HTTPS 응답의 HSTS
- 공통 형식의 401·403 JSON 응답

CORS는 [`CorsProperties`](../../src/main/java/com/gayadi/server/config/security/CorsProperties.java)로 Origin allowlist를 관리한다. 현재 기본값은 빈 목록이므로 브라우저의 교차 출처 요청을 허용하지 않는다. Android 네이티브 HTTP 클라이언트는 CORS 제약의 대상이 아니다. 향후 웹 Origin이 확정되면 `CORS_ALLOWED_ORIGINS`에 HTTPS Origin만 설정한다.

## 인증 토큰

[`JwtService`](../../src/main/java/com/gayadi/server/auth/JwtService.java)는 HS256 Access Token을 발급하고 서명·헤더·표준 claim을 검증한다. [`JwtAuthenticationFilter`](../../src/main/java/com/gayadi/server/auth/JwtAuthenticationFilter.java)는 `sub`를 사용자 ID로 사용하고 DB에서 활성 계정인지 다시 확인한다.

토큰에는 이메일을 넣지 않는다. 세부 claim, 설정과 배포 주의사항은 [JWT 보안 기준](jwt-security.md)을 따른다.

## Redis 보안 저장소

Spring Data Redis와 Lettuce 연결 설정이 준비되어 있다. 보안 저장소는 기본적으로 비활성화되며, 활성화하면 용도별 key namespace와 필수 TTL을 강제한다. Refresh Token·RTR·rate limit 기능은 아직 이 저장소에 연결되지 않았다. 상세 설정은 [Redis 보안 기반](redis-security.md)을 확인한다.

## 입력과 데이터 보호

- DTO에서 필수값, 길이, 범위, 형식과 목록 원소 수를 제한한다.
- 사용자가 제공하는 클릭 가능 URL은 `@HttpUrl`로 HTTP·HTTPS 절대 URL만 허용할 수 있다.
- Android 일반 텍스트에 웹용 XSS 삭제 필터를 적용하지 않는다.
- SQL은 바인딩 parameter를 사용하고 동적 SQL 조각은 고정값·enum allowlist로 제한한다.
- 비밀번호는 BCrypt 단방향 해시로 저장한다.
- 로그의 토큰·비밀번호·쿠키·API key는 제거하고 이메일·전화번호 등은 마스킹한다.
- Validation 오류 응답에 사용자가 입력한 `rejectedValue`를 포함하지 않는다.

입력·링크는 [Android 입력과 링크 보안](xss-input-policy.md), 로그·DB 데이터는 [민감정보 보호](sensitive-data-protection.md)를 확인한다.

## 테스트

| 테스트 | 보장 범위 |
|---|---|
| `WebSecurityPolicyIntegrationTests` | CORS, 보안 헤더와 인증 경계 |
| `AuthFlowIntegrationTests` | 가입·로그인·잘못된 JWT·계정 잠금 |
| `GoogleAuthFlowIntegrationTests` | Google ID Token 로그인과 안전한 오류 응답 |
| `JwtServiceTest` | 서명, header, issuer, audience, token type과 만료 |
| `ControllerRequestValidationTests` | DTO 요청 크기·형식 제한 |
| `SensitiveDataMaskerTest` | 로그 민감정보 마스킹 |

## 현재 경계

현재 보안 기반은 신규 API를 개발하기 전에 사용할 수 있는 전역 기준이다. 다음은 별도 구현과 운영 검증이 필요하다.

- Refresh Token·Redis·RTR·로그아웃 세션 폐기
- endpoint별 rate limit과 구조화된 보안 이벤트
- 전체 도메인 IDOR/BOLA 인가 감사
- 필요한 개인정보의 KMS 기반 DB 암호화
- CI 취약점·secret·container image 검사
- 운영 HTTPS·proxy·CORS·CSP·모니터링 검증
