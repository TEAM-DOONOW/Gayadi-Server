# JWT 보안 기준

## 현재 Access Token

GAYADI API는 Android 앱이 `Authorization: Bearer {token}`으로 전달하는 HS256 JWT를 사용한다.

| 항목 | 기준 |
|---|---|
| 서명 알고리즘 | `HS256` 고정 |
| `typ` | `JWT` |
| `sub` | 0보다 큰 내부 사용자 ID |
| `iss` | `app.jwt.issuer` |
| `aud` | `app.jwt.audience` |
| `token_type` | `access` |
| `jti` | 토큰별 UUID |
| `iat`, `exp` | 발급·만료 시각, 순서와 만료 검증 |

토큰 원문과 파싱 예외는 응답·로그에 노출하지 않는다.

## 이메일 claim을 넣지 않는 이유

현재 Access Token은 서명된 JWS이지 암호화된 JWE가 아니다. 서명은 위변을 방지하지만 payload를 숨기지 않으므로, 토큰을 접한 주체는 이메일을 별도 키 없이 읽을 수 있다.

GAYADI는 이메일 claim을 제거한다.

- 인증·인가는 `sub`의 내부 사용자 ID와 DB의 활성 상태로 판단하며 이메일을 사용하지 않는다.
- 토큰이 Android 로그, crash report, proxy, clipboard 또는 디버깅 도구에 노출되었을 때 함께 노출되는 개인정보를 줄인다.
- 이메일은 변경될 수 있지만 발급된 JWT는 만료 전까지 이전 값을 가져 프로필과 불일치할 수 있다.
- 토큰 크기와 매 요청의 전송량을 불필요하게 늘리지 않는다.

Android 화면에 이메일이 필요하면 JWT를 decode하여 표시하지 말고, 인증된 프로필 API에서 현재 DB 값을 조회한다. 향후 다른 서비스가 이메일을 claim으로 반드시 소비해야 한다면, 수신자·목적·보존 필요성을 보안 리뷰한 후에만 다시 추가한다.

근거:

- [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519)의 JWT는 claim 전달 형식이며 기밀성이 필요하면 암호화된 JWE가 필요하다.
- [RFC 8725 2.4](https://www.rfc-editor.org/rfc/rfc8725.html#section-2.4)는 JWT의 기밀성을 오해해 민감정보가 노출될 수 있음을 지적한다.

## 설정

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:}
    expires-in-seconds: ${JWT_EXPIRES_IN_SECONDS:604800}
    issuer: ${JWT_ISSUER:gayadi-server}
    audience: ${JWT_AUDIENCE:gayadi-android}
```

운영·외부 DB 환경은 32byte 이상의 예측할 수 없는 비밀키를 반드시 외부 설정으로 주입한다. 개발 환경에서 비밀키가 없으면 실행할 때마다 임시 키를 생성하므로 재시작 후 기존 토큰은 무효하다.

## 배포 주의사항

이전 JWT는 `iss`, `aud`, `jti`, `token_type`이 없어 강화된 검증을 통과하지 못한다. 배포 후 기존 사용자는 한 번 다시 로그인해야 한다. 호환 기간을 두고 기존 토큰을 허용하는 정책은 적용하지 않는다.

## Redis·RTR 전환 전까지

- Refresh Token은 아직 발급하지 않는다.
- Refresh Token 없이 Access Token TTL을 짧게 줄이면 Android 사용자가 자주 재로그인해야 하므로 기본 7일을 유지한다.
- Redis Refresh 세션과 RTR을 구현할 때 Access Token TTL을 짧게 조정한다.
- 키 교체가 필요하면 `kid`와 복수 검증 키 기간을 설계한다.
- 표준 JOSE/OAuth2 Resource Server 전환은 Redis·RTR 설계와 함께 재검토한다.
