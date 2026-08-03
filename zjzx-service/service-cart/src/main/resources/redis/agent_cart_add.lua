local ttl_seconds = tonumber(ARGV[1])
local fingerprint = ARGV[2]
local sku_field = ARGV[3]
local requested_quantity = tonumber(ARGV[4])
local maximum_total_quantity = tonumber(ARGV[5])
local new_item_json = ARGV[6]

if not ttl_seconds or ttl_seconds <= 0
        or not requested_quantity or requested_quantity <= 0
        or not maximum_total_quantity or maximum_total_quantity <= 0 then
    return redis.error_reply('INVALID_AGENT_CART_ARGUMENT')
end

local existing_fingerprint = redis.call('GET', KEYS[2])
if existing_fingerprint then
    if existing_fingerprint == fingerprint then
        return -1
    end
    return -2
end

local raw = redis.call('HGET', KEYS[1], sku_field)
local item
local resulting_quantity

if raw then
    local decoded_ok
    decoded_ok, item = pcall(cjson.decode, raw)
    if not decoded_ok or type(item) ~= 'table' then
        return redis.error_reply('INVALID_CART_ITEM_JSON')
    end
    local current_quantity = tonumber(item.skuNum)
    if not current_quantity or current_quantity < 1 then
        return redis.error_reply('INVALID_CART_ITEM_QUANTITY')
    end
    resulting_quantity = current_quantity + requested_quantity
    if resulting_quantity > maximum_total_quantity then
        return -3
    end
    item.skuNum = resulting_quantity
    item.isChecked = 1

    local new_item_ok, new_item = pcall(cjson.decode, new_item_json)
    if not new_item_ok or type(new_item) ~= 'table' then
        return redis.error_reply('INVALID_NEW_CART_ITEM_JSON')
    end
    item.updateTime = new_item.updateTime
else
    local decoded_ok
    decoded_ok, item = pcall(cjson.decode, new_item_json)
    if not decoded_ok or type(item) ~= 'table' then
        return redis.error_reply('INVALID_NEW_CART_ITEM_JSON')
    end
    resulting_quantity = requested_quantity
    if resulting_quantity > maximum_total_quantity then
        return -3
    end
end

redis.call('HSET', KEYS[1], sku_field, cjson.encode(item))
redis.call('SET', KEYS[2], fingerprint, 'EX', ttl_seconds)
return resulting_quantity
