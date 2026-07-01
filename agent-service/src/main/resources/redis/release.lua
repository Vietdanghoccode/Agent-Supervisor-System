local requested_agent = ARGV[1]
local conversation_id = ARGV[2]
local retention = tonumber(ARGV[3])
local prefix = 'agent:' .. requested_agent .. ':'
if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    return {'PROFILE_NOT_FOUND'}
end

local request_key = 'request:' .. conversation_id
local request_status = redis.call('HGET', request_key, 'status')
if not request_status or request_status == 'RELEASED' or request_status == 'CANCELLED'
    or request_status == 'EXPIRED' then
    return {'ALREADY_RELEASED'}
end
local owner = redis.call('HGET', request_key, 'agent_id')
if not owner then return {'ALREADY_RELEASED'} end
if owner ~= requested_agent then
    return {'OWNER_MISMATCH', owner}
end

redis.call('DEL', 'reservation:' .. conversation_id)
redis.call('ZREM', 'reservation_expiry_index', conversation_id)
for _, skill in ipairs(redis.call('SMEMBERS', prefix .. 'skills')) do
    redis.call('SREM', 'available_agents:' .. skill, requested_agent)
end
redis.call('HSET', request_key, 'status', 'RELEASED', 'agent_id', requested_agent)
redis.call('EXPIRE', request_key, retention)
local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')
if current > 0 then
    current = redis.call('DECR', prefix .. 'current_conversations')
else
    redis.call('SET', prefix .. 'current_conversations', '0')
    current = 0
end

local status = redis.call('GET', prefix .. 'status')
if status == 'waiting_to_break' and current == 0 then
    status = 'break'
    redis.call('SET', prefix .. 'status', status)
end

local state = redis.call('GET', prefix .. 'state')
local max = tonumber(redis.call('GET', prefix .. 'max_conversations'))
return {'RELEASED', tostring(current), status}
