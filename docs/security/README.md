# GAYADI 보안 문서

이 디렉터리는 GAYADI Android API에 현재 적용된 보안 구조와 향후 강화 기준을 설명한다.

| 문서 | 내용 |
|---|---|
| [현재 보안 구조](security-overview.md) | HTTP 경계, JWT, 입력·로그 보호의 실제 동작 |
| [민감정보 보호](sensitive-data-protection.md) | 로그 마스킹과 DB 저장 시 보호 기준 |
| [Android 입력과 링크 보안](xss-input-policy.md) | Android 텍스트, 외부 링크와 향후 WebView 처리 기준 |
| [JWT 보안](jwt-security.md) | Access Token claim, 검증 조건과 Redis·RTR 전환 기준 |
| [Redis 보안 기반](redis-security.md) | 연결 설정, key namespace, TTL과 장애 처리 기준 |
| [보안 강화 로드맵](security-hardening-roadmap.md) | 전역 보안 정책, JWT·Redis·RTR와 DB 암호화 구현 순서 |

새 API를 개발할 때는 먼저 [현재 보안 구조](security-overview.md)를 확인한다. 아직 구현되지 않은 기능은 [보안 강화 로드맵](security-hardening-roadmap.md)의 순서와 완료 조건을 따른다.
