local conversation_id = ARGV[1]
local retention = tonumber(ARGV[2])
local request_key = 'request:' .. conversation_id

-- A due index entry can be observed just before Redis removes the TTL key.
if redis.call('EXISTS', 'reservation:' .. conversation_id) == 1 then
    return {'NOT_EXPIRED'}
end

local status = redis.call('HGET', request_key, 'status')
local agent_id = redis.call('HGET', request_key, 'agent_id')
if status ~= 'RESERVED' or not agent_id then
    redis.call('ZREM', 'reservation_expiry_index', conversation_id)
    return {'ALREADY_RELEASED'}
end

local prefix = 'agent:' .. agent_id .. ':'
redis.call('ZREM', 'reservation_expiry_index', conversation_id)
for _, skill in ipairs(redis.call('SMEMBERS', prefix .. 'skills')) do
    redis.call('SREM', 'available_agents:' .. skill, agent_id)
end
local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')
if current > 0 then
    current = redis.call('DECR', prefix .. 'current_conversations')
end

local source = redis.call('HGET', request_key, 'source')
if source == 'QUEUE' then
    local skill = redis.call('HGET', request_key, 'skill')
    local sequence = redis.call('HGET', request_key, 'sequence')
    redis.call('HDEL', request_key, 'agent_id')
    redis.call('HSET', request_key, 'status', 'WAITING')
    redis.call('PERSIST', request_key)
    redis.call('ZADD', 'waiting_requests:' .. skill, sequence, conversation_id)
else
    if redis.call('EXISTS', request_key) == 1 then
        redis.call('HSET', request_key, 'status', 'EXPIRED')
        redis.call('EXPIRE', request_key, retention)
    end
end

local status = redis.call('GET', prefix .. 'status')
if status == 'waiting_to_break' and current == 0 then
    status = 'break'
    redis.call('SET', prefix .. 'status', status)
end
local state = redis.call('GET', prefix .. 'state')
local max = tonumber(redis.call('GET', prefix .. 'max_conversations') or '0')
return {'RELEASED', agent_id}
