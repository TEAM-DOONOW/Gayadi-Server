# CI 보안 검사

일반 push와 pull request에서 빌드·테스트, CodeQL과 Git 이력 secret 검사를 실행한다. Pull request에서는 HIGH 이상 취약 의존성 변경을 차단한다. Dependabot은 Gradle과 GitHub Actions 업데이트를 매주 제안한다.

배포 워크플로는 이미지를 Registry에 올리기 전에 HIGH·CRITICAL 취약점을 차단하고 SBOM을 생성한다. 이미지는 immutable SHA tag로 push한 뒤 keyless Cosign 서명과 SBOM attestation을 연결한다.

## 저장소 설정

- branch protection에서 `test`, `dependency-review`, `codeql`, `secret-scan`을 필수 검사로 지정한다.
- GitHub secret scanning과 push protection을 활성화한다.
- workflow token 기본 권한은 read-only로 유지하고 필요한 job에만 `security-events: write`, `id-token: write`를 부여한다.
- Actions 태그 변경 위험을 줄이려면 조직 정책에서 검증된 Action만 허용하고 Dependabot 업데이트를 리뷰한다.
- 발견된 credential은 파일 삭제만 하지 말고 먼저 발급처에서 폐기·교체한다.
