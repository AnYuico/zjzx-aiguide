local limits = {
    tonumber(ARGV[1]),
    tonumber(ARGV[2]),
    tonumber(ARGV[3])
}
local ttl_seconds = tonumber(ARGV[4])

if not ttl_seconds or ttl_seconds <= 0 then
    return redis.error_reply('INVALID_RATE_LIMIT_TTL')
end

for index = 1, #KEYS do
    local current = redis.call('INCR', KEYS[index])
    if current == 1 then
        redis.call('EXPIRE', KEYS[index], ttl_seconds)
    end
    if not limits[index] or limits[index] <= 0 or current > limits[index] then
        return index
    end
end

return 0

