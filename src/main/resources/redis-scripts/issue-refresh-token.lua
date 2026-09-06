-- 최초 Refresh Token 상태를 Redis Hash로 저장하고 TTL을 설정한다.
--
-- KEYS[1]: Refresh Token 상태 Hash key
--          {prefix}:auth:refresh:{tokenId}
--
-- ARGV[1]: Refresh Token secret의 SHA-256 해시
-- ARGV[2]: 내부 사용자 ID
-- ARGV[3]: 기기별 로그인 세션 ID
-- ARGV[4]: 토큰 재사용 탐지 단위인 family ID
-- ARGV[5]: 세션 절대 만료 시각(epoch seconds)
-- ARGV[6]: 현재 토큰의 TTL(seconds)
--
-- 반환값: 1 = 저장 완료

-- Hash 저장과 TTL 설정을 하나의 원자 작업으로 수행한다.
redis.call('HSET', KEYS[1],
    'hash', ARGV[1],
    'userId', ARGV[2],
    'session', ARGV[3],
    'family', ARGV[4],
    'absoluteExpiry', ARGV[5],
    'status', 'ACTIVE')
redis.call('EXPIRE', KEYS[1], ARGV[6])
return 1
