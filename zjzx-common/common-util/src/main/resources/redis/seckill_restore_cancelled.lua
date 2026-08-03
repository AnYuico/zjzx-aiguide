if redis.call('HEXISTS', KEYS[3], ARGV[1]) == 1 then
    return 0
end

redis.call('HSET', KEYS[3], ARGV[1], '1')
redis.call('INCR', KEYS[1])

local raw_result = redis.call('HGET', KEYS[2], ARGV[1])
local result = {
    requestId = ARGV[1],
    userId = ARGV[2],
    orderNo = ARGV[3],
    status = 4,
    message = 'CANCELLED'
}
if raw_result then
    local decoded_ok, decoded = pcall(cjson.decode, raw_result)
    if decoded_ok then
        result = decoded
    end
end
result.status = 4
result.message = 'CANCELLED'
redis.call('HSET', KEYS[2], ARGV[1], cjson.encode(result))

local ttl_seconds = tonumber(ARGV[4])
if ttl_seconds and ttl_seconds > 0 then
    for index = 1, #KEYS do
        redis.call('EXPIRE', KEYS[index], ttl_seconds)
    end
end
return 1
