local limits = {}
for index = 1, #KEYS do
    limits[index] = tonumber(ARGV[index])
end
local ttl_seconds = tonumber(ARGV[#KEYS + 1])

if not ttl_seconds or ttl_seconds <= 0 then
    return redis.error_reply('INVALID_RATE_LIMIT_TTL')
end

for index = 1, #KEYS do
    local current = tonumber(redis.call('GET', KEYS[index]) or '0')
    if not limits[index] or limits[index] <= 0 or current >= limits[index] then
        return index
    end
end

for index = 1, #KEYS do
    local current = redis.call('INCR', KEYS[index])
    if current == 1 then
        redis.call('EXPIRE', KEYS[index], ttl_seconds)
    end
end

return 0
