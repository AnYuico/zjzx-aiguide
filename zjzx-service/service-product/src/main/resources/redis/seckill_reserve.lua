local status = tonumber(redis.call('HGET', KEYS[1], 'status'))
local start_at = tonumber(redis.call('HGET', KEYS[1], 'startAt'))
local end_at = tonumber(redis.call('HGET', KEYS[1], 'endAt'))
local now = tonumber(ARGV[5])
local ttl_seconds = tonumber(ARGV[7])

if status ~= 1 or not start_at or not end_at or not now
        or now < start_at or now >= end_at then
    return -1
end

local existing_payload = redis.call('HGET', KEYS[4], ARGV[2])
if existing_payload then
    local owner_request = redis.call('HGET', KEYS[3], ARGV[1])
    if owner_request == ARGV[2] then
        return 2
    end
    return -4
end

local existing_request = redis.call('HGET', KEYS[3], ARGV[1])
if existing_request then
    if existing_request == ARGV[2] then
        return 2
    end
    return -3
end

local stock = tonumber(redis.call('GET', KEYS[2]))
if not stock or stock <= 0 then
    return -2
end

redis.call('DECR', KEYS[2])
redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])
redis.call('HSET', KEYS[4], ARGV[2], ARGV[6])
redis.call('HSET', KEYS[5], ARGV[2], cjson.encode({
    requestId = ARGV[2],
    userId = ARGV[1],
    orderNo = ARGV[3],
    status = 0,
    message = 'QUEUED'
}))
redis.call('ZADD', KEYS[6], now, ARGV[2])

if ttl_seconds and ttl_seconds > 0 then
    for index = 1, #KEYS do
        redis.call('EXPIRE', KEYS[index], ttl_seconds)
    end
end

return 1
