local agent_id = ARGV[1]
local prefix = 'agent:' .. agent_id .. ':'
if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    return {'PROFILE_NOT_FOUND'}
end

redis.call('SET', prefix .. 'state', 'online')
local status = redis.call('GET', prefix .. 'status')
local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')
local max = tonumber(redis.call('GET', prefix .. 'max_conversations'))
return {'OK'}
