local agent_id = ARGV[1]
local requested_max = tonumber(ARGV[2])
local prefix = 'agent:' .. agent_id .. ':'
local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')

if requested_max < current then
    return {'MAX_BELOW_CURRENT', tostring(current)}
end

local old_skills = redis.call('SMEMBERS', prefix .. 'skills')
for _, skill in ipairs(old_skills) do
    redis.call('SREM', 'available_agents:' .. skill, agent_id)
end

local cursor = 3
local skill_count = tonumber(ARGV[cursor])
cursor = cursor + 1
local skills = {}
for i = 1, skill_count do
    skills[i] = ARGV[cursor]
    cursor = cursor + 1
end

local team_count = tonumber(ARGV[cursor])
cursor = cursor + 1
local teams = {}
for i = 1, team_count do
    teams[i] = ARGV[cursor]
    cursor = cursor + 1
end

local channel_count = tonumber(ARGV[cursor])
cursor = cursor + 1
local channels = {}
for i = 1, channel_count do
    channels[i] = ARGV[cursor]
    cursor = cursor + 1
end

redis.call('DEL', prefix .. 'skills', prefix .. 'teams', prefix .. 'channels')
for _, skill in ipairs(skills) do
    redis.call('SADD', prefix .. 'skills', skill)
end
for _, team in ipairs(teams) do
    redis.call('SADD', prefix .. 'teams', team)
end
for _, channel in ipairs(channels) do
    redis.call('SADD', prefix .. 'channels', channel)
end

if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    redis.call('SET', prefix .. 'state', 'offline')
    redis.call('SET', prefix .. 'status', 'available')
    redis.call('SET', prefix .. 'current_conversations', '0')
end
redis.call('SET', prefix .. 'max_conversations', tostring(requested_max))

local state = redis.call('GET', prefix .. 'state')
local status = redis.call('GET', prefix .. 'status')
if state == 'online' and status == 'available' and current < requested_max then
    for _, skill in ipairs(skills) do
        redis.call('SADD', 'available_agents:' .. skill, agent_id)
    end
end

return {'OK'}
