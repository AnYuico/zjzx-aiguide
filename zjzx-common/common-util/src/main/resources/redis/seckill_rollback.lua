if redis.call('HEXISTS', KEYS[7], ARGV[2]) == 1 then
    return 0
end

local buyer_request = redis.call('HGET', KEYS[2], ARGV[1])
if buyer_request ~= ARGV[2] then
    return -1
end

redis.call('HSET', KEYS[7], ARGV[2], '1')
redis.call('INCR', KEYS[1])
redis.call('ZREM', KEYS[5], ARGV[2])
redis.call('HDEL', KEYS[6], ARGV[2])

local raw_result = redis.call('HGET', KEYS[4], ARGV[2])
local result = {
    requestId = ARGV[2],
    userId = ARGV[1],
    orderNo = ARGV[3],
    status = 3,
    message = ARGV[4]
}
if raw_result then
    local decoded_ok, decoded = pcall(cjson.decode, raw_result)
    if decoded_ok then
        result = decoded
        result.status = 3
        result.orderId = nil
        result.message = ARGV[4]
    end
end
redis.call('HSET', KEYS[4], ARGV[2], cjson.encode(result))

local ttl_seconds = tonumber(ARGV[5])
if ttl_seconds and ttl_seconds > 0 then
    for index = 1, #KEYS do
        redis.call('EXPIRE', KEYS[index], ttl_seconds)
    end
end
return 1
