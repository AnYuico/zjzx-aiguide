local ttl_seconds = tonumber(ARGV[1])
if not ttl_seconds or ttl_seconds <= 0 then
    return redis.error_reply('INVALID_IDEMPOTENCY_TTL')
end

if redis.call('EXISTS', KEYS[2]) == 1 then
    return -1
end

local updates = {}
for index = 2, #ARGV, 2 do
    local field = ARGV[index]
    local purchased = tonumber(ARGV[index + 1])
    if not purchased or purchased <= 0 then
        return redis.error_reply('INVALID_PURCHASE_QUANTITY')
    end

    local raw = redis.call('HGET', KEYS[1], field)
    if raw then
        local decoded_ok, item = pcall(cjson.decode, raw)
        if not decoded_ok or type(item) ~= 'table' then
            return redis.error_reply('INVALID_CART_ITEM_JSON:' .. field)
        end

        local current = tonumber(item.skuNum)
        if not current or current <= 0 then
            return redis.error_reply('INVALID_CART_ITEM_QUANTITY:' .. field)
        end

        table.insert(updates, {
            field = field,
            item = item,
            remaining = current - purchased
        })
    end
end

for _, update in ipairs(updates) do
    if update.remaining <= 0 then
        redis.call('HDEL', KEYS[1], update.field)
    else
        update.item.skuNum = update.remaining
        update.item.isChecked = 0
        redis.call('HSET', KEYS[1], update.field, cjson.encode(update.item))
    end
end

redis.call('SET', KEYS[2], '1', 'EX', ttl_seconds)
return #updates
