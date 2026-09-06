-- KEYS[1]: 환경·API 종류·클라이언트 주소별 요청 횟수 key
-- ARGV[1]: 현재 구간에서 허용할 최대 요청 수
-- ARGV[2]: 고정 구간 TTL(초)
-- 반환: 허용 1, 한도 초과 0

local count = redis.call('INCR', KEYS[1])
if count == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end

if count > tonumber(ARGV[1]) then
    return 0
end

return 1
