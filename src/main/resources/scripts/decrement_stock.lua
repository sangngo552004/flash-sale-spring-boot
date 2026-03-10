-- KEYS[1]: Tên key (VD: product_stock:1)
-- ARGV[1]: Số lượng trừ (VD: 1)

local current_stock = tonumber(redis.call('get', KEYS[1]))
if current_stock == nil then return -1 end

if current_stock >= tonumber(ARGV[1]) then
    return redis.call('decrby', KEYS[1], ARGV[1])
else
    return -2
end