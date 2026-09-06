-- 기존 Refresh Token을 한 번 소비하고 같은 세션·family의 새 토큰으로 교체한다.
--
-- KEYS[1]: 기존 Refresh Token 상태 Hash key
-- KEYS[2]: token family 폐기 상태 key
-- KEYS[3]: 현재 로그인 세션 폐기 상태 key
-- KEYS[4]: 새 Refresh Token 상태 Hash key
--
-- ARGV[1]: 전달받은 기존 token secret의 SHA-256 해시
-- ARGV[2]: 새 token secret의 SHA-256 해시
-- ARGV[3]: 새 토큰과 폐기 상태에 적용할 TTL(seconds)
--
-- 반환값:
--   양수 = 회전에 성공한 내부 사용자 ID
--      0 = 토큰이 없거나 해시가 일치하지 않음
--     -1 = 토큰·세션·family가 이미 폐기됨
--     -2 = 사용 완료 토큰의 재사용을 감지하고 family를 폐기함

-- 기존 토큰이 없으면 유효하지 않은 요청으로 종료한다.
local status = redis.call('HGET', KEYS[1], 'status')
if not status then
    return 0
end

local family = redis.call('HGET', KEYS[1], 'family')

-- 해시를 먼저 검증하여 tokenId만 아는 요청이 family를 폐기하지 못하게 한다.
if redis.call('HGET', KEYS[1], 'hash') ~= ARGV[1] then
    return 0
end

if status == 'USED' then
    -- 정상적으로 소비된 토큰이 다시 들어오면 탈취 가능성이 있어 family 전체를 폐기한다.
    redis.call('SET', KEYS[2], '1', 'EX', ARGV[3])
    return -2
end

if status ~= 'ACTIVE' or redis.call('GET', KEYS[2]) or redis.call('GET', KEYS[3]) then
    return -1
end

-- 기존 토큰을 재사용할 수 없도록 표시한 뒤 새 토큰에 세션 정보를 승계한다.
redis.call('HSET', KEYS[1], 'status', 'USED')
redis.call('HSET', KEYS[4],
    'hash', ARGV[2],
    'userId', redis.call('HGET', KEYS[1], 'userId'),
    'session', redis.call('HGET', KEYS[1], 'session'),
    'family', family,
    'absoluteExpiry', redis.call('HGET', KEYS[1], 'absoluteExpiry'),
    'status', 'ACTIVE')
redis.call('EXPIRE', KEYS[4], ARGV[3])
return tonumber(redis.call('HGET', KEYS[1], 'userId'))
