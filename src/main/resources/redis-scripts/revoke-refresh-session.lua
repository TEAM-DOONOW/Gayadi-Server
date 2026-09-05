-- Refresh Token의 소유권을 검증하고 해당 기기의 로그인 세션을 폐기한다.
--
-- KEYS[1]: 폐기 요청에 사용한 Refresh Token 상태 Hash key
-- KEYS[2]: 현재 로그인 세션 폐기 상태 key
--
-- ARGV[1]: 전달받은 token secret의 SHA-256 해시
-- ARGV[2]: 세션 폐기 상태를 유지할 TTL(seconds)
--
-- 반환값:
--   1 = 세션 폐기 완료
--   0 = 토큰이 없거나 해시가 일치하지 않음

-- tokenId만 아는 요청이 다른 사용자의 세션을 종료하지 못하도록 해시를 검증한다.
if redis.call('HGET', KEYS[1], 'hash') ~= ARGV[1] then
    return 0
end

-- 남아 있는 Refresh Token이 모두 거부되도록 세션 폐기 상태를 저장한다.
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
return 1
