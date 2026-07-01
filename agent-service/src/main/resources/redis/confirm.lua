local conversation_id = ARGV[1]
local retention = tonumber(ARGV[2])
local reservation_key = 'reservation:' .. conversation_id
local request_key = 'request:' .. conversation_id

if redis.call('HGET', request_key, 'status') == 'CONFIRMED' then
    return {'CONFIRMED', redis.call('HGET', request_key, 'agent_id')}
end

local reserved_agent = redis.call('GET', reservation_key)
if not reserved_agent then
    return {'NOT_FOUND'}
end

redis.call('DEL', reservation_key)
redis.call('ZREM', 'reservation_expiry_index', conversation_id)
redis.call('HSET', request_key, 'status', 'CONFIRMED', 'agent_id', reserved_agent)
redis.call('PERSIST', request_key)
return {'CONFIRMED', reserved_agent}
