local agent_id = ARGV[1]
local prefix = 'agent:' .. agent_id .. ':'
if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    return {'PROFILE_NOT_FOUND'}
end
if redis.call('GET', prefix .. 'state') ~= 'online' then
    return {'OFFLINE'}
end

local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')
if current == 0 then
    redis.call('SET', prefix .. 'status', 'break')
else
    redis.call('SET', prefix .. 'status', 'waiting_to_break')
end
local skills = redis.call('SMEMBERS', prefix .. 'skills')
for _, skill in ipairs(skills) do
    redis.call('SREM', 'available_agents:' .. skill, agent_id)
end
return {'OK'}
