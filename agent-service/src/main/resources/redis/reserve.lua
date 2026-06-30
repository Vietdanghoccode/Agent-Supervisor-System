local conversation_id = ARGV[1]
local skill = ARGV[2]
local ttl = tonumber(ARGV[3])
local reservation_key = 'reservation:' .. conversation_id
local owner_key = 'reservation_owner:' .. conversation_id
local assignment_key = 'assignment:' .. conversation_id

local assigned_agent = redis.call('GET', assignment_key)
if assigned_agent then
    return {'CONFIRMED', assigned_agent, '0'}
end

local reserved_agent = redis.call('GET', reservation_key)
if reserved_agent then
    local remaining_ttl = redis.call('TTL', reservation_key)
    return {'RESERVED', reserved_agent, tostring(math.max(remaining_ttl, 0))}
end

-- The TTL may have elapsed while the expiry listener has not compensated yet.
-- Do not create a second reservation that an older expiry event could release.
local pending_owner = redis.call('GET', owner_key)
if pending_owner then
    return {'RECOVERY_PENDING', pending_owner, '0'}
end

local pool_key = 'available_agents:' .. skill
local pool_size = redis.call('SCARD', pool_key)
if pool_size == 0 then
    return {'NO_AGENT'}
end

local candidates = redis.call('SRANDMEMBER', pool_key, pool_size)
for _, agent_id in ipairs(candidates) do
    local prefix = 'agent:' .. agent_id .. ':'
    local state = redis.call('GET', prefix .. 'state')
    local status = redis.call('GET', prefix .. 'status')
    local current_raw = redis.call('GET', prefix .. 'current_conversations')
    local max_raw = redis.call('GET', prefix .. 'max_conversations')
    if state == 'online' and status == 'available' and current_raw and max_raw then
        local current = tonumber(current_raw)
        local max = tonumber(max_raw)
        if current < max then
            local after_increment = redis.call('INCR', prefix .. 'current_conversations')
            if after_increment == max then
                local skills = redis.call('SMEMBERS', prefix .. 'skills')
                for _, agent_skill in ipairs(skills) do
                    redis.call('SREM', 'available_agents:' .. agent_skill, agent_id)
                end
            end
            redis.call('SET', reservation_key, agent_id, 'EX', ttl)
            redis.call('SET', owner_key, agent_id)
            return {'RESERVED', agent_id, tostring(ttl)}
        end
    end
end

return {'NO_AGENT'}
