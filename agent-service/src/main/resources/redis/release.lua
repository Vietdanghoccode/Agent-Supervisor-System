local requested_agent = ARGV[1]
local conversation_id = ARGV[2]
local prefix = 'agent:' .. requested_agent .. ':'
if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    return {'PROFILE_NOT_FOUND'}
end

local reservation_key = 'reservation:' .. conversation_id
local owner_key = 'reservation_owner:' .. conversation_id
local assignment_key = 'assignment:' .. conversation_id
local owner = redis.call('GET', reservation_key)
if not owner then
    owner = redis.call('GET', assignment_key)
end
if not owner then
    owner = redis.call('GET', owner_key)
end
if not owner then
    return {'ALREADY_RELEASED'}
end
if owner ~= requested_agent then
    return {'OWNER_MISMATCH', owner}
end

redis.call('DEL', reservation_key, owner_key, assignment_key)
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
if state == 'online' and status == 'available' and current < max then
    local skills = redis.call('SMEMBERS', prefix .. 'skills')
    for _, skill in ipairs(skills) do
        redis.call('SADD', 'available_agents:' .. skill, requested_agent)
    end
end

return {'RELEASED', tostring(current), status}
