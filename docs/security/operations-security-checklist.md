# 운영 보안 체크리스트

## 배포 전

- HTTPS만 허용하고 HSTS가 실제 TLS 종단 응답에 적용되는지 확인한다.
- 신뢰 reverse proxy 목록과 실제 client IP 정규화를 완료하기 전에는 전달 헤더를 신뢰하지 않는다.
- 운영 CORS에는 필요한 HTTPS Origin만 등록하고 Android 네이티브 요청을 위해 wildcard를 열지 않는다.
- JWT 현재·이전 key ID가 다르고 key 값이 Git·이미지·로그에 없는지 확인한다.
- Redis 인증·TLS·private network·환경별 key prefix와 TTL을 확인한다.
- Swagger와 관리 API는 운영에서 비활성화하거나 별도 관리자 경계로 제한한다.
- DB backup 암호화와 복구 테스트, 최소 권한 DB 계정을 확인한다.

## 배포 후

- RTR 동시 요청 하나만 성공하는지, 재사용 시 family가 폐기되는지 확인한다.
- Rate Limit이 여러 서버에서 공유되고 429·503 응답에 내부 정보가 없는지 확인한다.
- 401·403·429 급증, Refresh 재사용과 관리자 API 접근을 민감정보 없이 경보한다.
- 이미지 digest와 서명, SBOM attestation을 검증한다.

## 사고 대응

1. 영향 범위와 노출된 credential 종류를 확인하고 원문을 티켓·채팅에 복사하지 않는다.
2. 외부 API key·DB·Redis·JWT key를 영향도 순서로 교체한다.
3. JWT key 유출 시 새 `kid`로 발급하고 필요하면 전체 Refresh family를 폐기한다.
4. 감사 로그와 배포 digest를 보존하고 원인 수정 후 재발 테스트를 추가한다.
5. 개인정보 사고 통지와 보존·파기는 조직의 법무·개인정보 담당 절차를 따른다.
