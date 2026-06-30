local agent_id = ARGV[1]
local prefix = 'agent:' .. agent_id .. ':'
if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    return {'PROFILE_NOT_FOUND'}
end

redis.call('SET', prefix .. 'state', 'offline')
local skills = redis.call('SMEMBERS', prefix .. 'skills')
for _, skill in ipairs(skills) do
    redis.call('SREM', 'available_agents:' .. skill, agent_id)
end
return {'OK'}
