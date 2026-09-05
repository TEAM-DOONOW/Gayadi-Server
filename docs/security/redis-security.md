# Redis 보안 기반

## 용도

Redis는 향후 Refresh Token Rotation, 로그아웃 세션 폐기와 인증 endpoint rate limit에 사용한다. 현재는 연결 설정과 TTL이 강제된 보안 저장소만 준비되어 있으며 Refresh Token은 아직 발급하지 않는다.

## 활성화

Spring Boot는 `/run/secrets/`를 Config Tree로 읽는다. Docker Compose의 `redis_password` secret은 Spring property `redis_password`가 되며, 파일이 없는 로컬 실행에서는 `REDIS_PASSWORD` 환경변수를 fallback으로 사용한다.

```yaml
spring:
  config:
    import:
      - optional:file:.env[.properties]
      - optional:configtree:/run/secrets/
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      username: ${REDIS_USERNAME:}
      password: ${redis_password:${REDIS_PASSWORD:}}
      connect-timeout: ${REDIS_CONNECT_TIMEOUT:2s}
      timeout: ${REDIS_READ_TIMEOUT:2s}
      ssl:
        enabled: ${REDIS_SSL_ENABLED:false}

app:
  security:
    redis:
      enabled: ${SECURITY_REDIS_ENABLED:false}
      key-prefix: ${SECURITY_REDIS_KEY_PREFIX:gayadi:local:security}
      max-ttl: ${SECURITY_REDIS_MAX_TTL:90d}
```

`SECURITY_REDIS_ENABLED` 기본값은 `false`다. 따라서 Redis가 없는 기본 H2 개발·테스트 실행은 영향을 받지 않는다. Redis 보안 기능을 사용하는 환경에서만 `true`로 설정한다.

같은 설정으로 Redis health indicator도 활성화된다. Redis 보안 기능을 켠는데 Redis에 연결할 수 없으면 `/actuator/health`는 정상을 보고하지 않는다.

`docker-compose-db.yaml`의 Redis는 로컬 개발용이다. 운영 Redis는 외부 노출을 금지하고 private network, 인증, TLS와 접근제어를 적용한다.

## Secret 파일 배치

현재 Compose는 host의 `secrets/` 디렉터리를 백엔드 컨테이너의 `/run/secrets/`에 읽기 전용으로 mount한다. 백엔드는 아래 파일명을 Spring Config Tree property로 사용한다.

```text
secrets/
├─ db_password
├─ redis_password
├─ jwt_secret
├─ tour_api_key
├─ congestion_api_key
├─ weather_api_key
├─ groq_api_key
└─ skt_appkey
```

- `secrets/`는 Git과 Docker build context에 포함하지 않는다.
- secret 파일은 마지막 줄바꿈 외에 인용부호·설정 key·주석을 넣지 않고 값만 저장한다.
- `.env.dev`와 `.env.prod`는 host·port·기능 활성화 같은 비밀이 아닌 설정만 관리한다.
- 백엔드는 Spring Config Tree를 통해 secret을 설정으로만 주입받고 파일 내용을 로그로 출력하지 않는다.

## Key 규칙

[`SecurityRedisStore`](../../src/main/java/com/gayadi/server/common/security/redis/SecurityRedisStore.java)는 임의 key 생성과 TTL 없는 저장을 허용하지 않는다.

```text
{environment-prefix}:auth:session:{sessionId}
{environment-prefix}:auth:refresh:{tokenId}
{environment-prefix}:auth:rate-limit:{bucketId}
```

- prefix는 환경별로 다르게 설정한다.
- namespace는 [`SecurityRedisNamespace`](../../src/main/java/com/gayadi/server/common/security/redis/SecurityRedisNamespace.java)에 정의된 값만 사용한다.
- key ID는 영문·숫자·`_`·`-`만 사용하며 128자를 넘지 않는다.
- 모든 `put` 호출은 0보다 크고 `max-ttl`보다 작거나 같은 TTL을 반드시 전달한다.

## 저장 금지 데이터

- Access·Refresh·Google ID Token 원문
- 비밀번호와 외부 API credential
- 이메일·전화번호·주소 등 인증 상태에 필요하지 않은 개인정보

Refresh Token은 원문 대신 해시만 저장한다. 세션 값은 내부 사용자 ID, 세션·token family 식별자와 만료 정보로 최소화한다.

## 장애 정책

- Refresh Token 갱신·재사용 검증은 Redis 장애 시 fail-closed로 처리한다.
- 로그인·초대 코드 rate limit은 보안과 가용성 요구를 따로 확정한 후 장애 정책을 적용한다.
- Redis 예외에 접속 정보나 저장값을 포함해 로그로 출력하지 않는다.

## 다음 구현

Refresh Token Rotation은 단순 `get` 후 `put`으로 구현하지 않는다. 동시 요청에서 하나의 Refresh Token만 성공하도록 Lua script나 Redis transaction으로 소비·교체를 원자적으로 구현한다.
