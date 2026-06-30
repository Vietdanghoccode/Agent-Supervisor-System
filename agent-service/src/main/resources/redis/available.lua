local agent_id = ARGV[1]
local prefix = 'agent:' .. agent_id .. ':'
if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    return {'PROFILE_NOT_FOUND'}
end
if redis.call('GET', prefix .. 'state') ~= 'online' then
    return {'OFFLINE'}
end

redis.call('SET', prefix .. 'status', 'available')
local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')
local max = tonumber(redis.call('GET', prefix .. 'max_conversations'))
if current < max then
    local skills = redis.call('SMEMBERS', prefix .. 'skills')
    for _, skill in ipairs(skills) do
        redis.call('SADD', 'available_agents:' .. skill, agent_id)
    end
end
return {'OK'}
