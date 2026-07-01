local conversation_id = ARGV[1]
local skill = ARGV[2]
local ttl = tonumber(ARGV[3])
local now = ARGV[4]
local request_key = 'request:' .. conversation_id
local reservation_key = 'reservation:' .. conversation_id
local expiry_index = 'reservation_expiry_index'

local request_status = redis.call('HGET', request_key, 'status')
if request_status then
    local request_skill = redis.call('HGET', request_key, 'skill')
    if request_skill and request_skill ~= skill then
        return {'SKILL_CONFLICT', '', '0'}
    end
    local agent_id = redis.call('HGET', request_key, 'agent_id') or ''
    local remaining_ttl = redis.call('TTL', reservation_key)
    return {request_status, agent_id, tostring(math.max(remaining_ttl, 0))}
end

local pool_key = 'available_agents:' .. skill
local candidates = redis.call('SRANDMEMBER', pool_key, redis.call('SCARD', pool_key))
for _, agent_id in ipairs(candidates) do
    local prefix = 'agent:' .. agent_id .. ':'
    local state = redis.call('GET', prefix .. 'state')
    local status = redis.call('GET', prefix .. 'status')
    local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '-1')
    local max = tonumber(redis.call('GET', prefix .. 'max_conversations') or '-1')
    if state == 'online' and status == 'available' and current >= 0 and current < max then
        local after_increment = redis.call('INCR', prefix .. 'current_conversations')
        if after_increment == max then
            for _, agent_skill in ipairs(redis.call('SMEMBERS', prefix .. 'skills')) do
                redis.call('SREM', 'available_agents:' .. agent_skill, agent_id)
            end
        end
        redis.call('SET', reservation_key, agent_id, 'EX', ttl)
        redis.call('ZADD', expiry_index, tonumber(now) + ttl * 1000, conversation_id)
        redis.call('HSET', request_key,
            'skill', skill, 'status', 'RESERVED', 'agent_id', agent_id,
            'source', 'DIRECT', 'created_at', now)
        return {'RESERVED', agent_id, tostring(ttl)}
    end
end

local sequence = redis.call('INCR', 'waiting_request_sequence')
redis.call('HSET', request_key,
    'skill', skill, 'status', 'WAITING', 'sequence', sequence,
    'source', 'QUEUE', 'created_at', now)
redis.call('ZADD', 'waiting_requests:' .. skill, sequence, conversation_id)
return {'WAITING', '', '0'}
