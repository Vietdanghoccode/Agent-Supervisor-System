local agent_id = ARGV[1]
local ttl = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local prefix = 'agent:' .. agent_id .. ':'
if redis.call('EXISTS', prefix .. 'max_conversations') == 0 then
    return {'PROFILE_NOT_FOUND', '0'}
end

-- Keep the agent hidden from direct reserve while queued work gets first chance.
for _, skill in ipairs(redis.call('SMEMBERS', prefix .. 'skills')) do
    redis.call('SREM', 'available_agents:' .. skill, agent_id)
end

local assigned = 0
while redis.call('GET', prefix .. 'state') == 'online'
    and redis.call('GET', prefix .. 'status') == 'available' do
    local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')
    local max = tonumber(redis.call('GET', prefix .. 'max_conversations') or '0')
    if current >= max then break end

    local selected_id = nil
    local selected_skill = nil
    local selected_score = nil
    for _, skill in ipairs(redis.call('SMEMBERS', prefix .. 'skills')) do
        local queue_key = 'waiting_requests:' .. skill
        local found = false
        while not found do
            local head = redis.call('ZRANGE', queue_key, 0, 0, 'WITHSCORES')
            if #head == 0 then break end
            local candidate = head[1]
            local request_key = 'request:' .. candidate
            if redis.call('HGET', request_key, 'status') == 'WAITING'
                and redis.call('HGET', request_key, 'skill') == skill then
                local score = tonumber(head[2])
                if not selected_score or score < selected_score then
                    selected_id = candidate
                    selected_skill = skill
                    selected_score = score
                end
                found = true
            else
                redis.call('ZREM', queue_key, candidate)
            end
        end
    end
    if not selected_id then break end

    redis.call('ZREM', 'waiting_requests:' .. selected_skill, selected_id)
    local after_increment = redis.call('INCR', prefix .. 'current_conversations')
    redis.call('SET', 'reservation:' .. selected_id, agent_id, 'EX', ttl)
    redis.call('ZADD', 'reservation_expiry_index', now + ttl * 1000, selected_id)
    redis.call('HSET', 'request:' .. selected_id, 'status', 'RESERVED', 'agent_id', agent_id)
    assigned = assigned + 1
    if after_increment >= max then break end
end

local current = tonumber(redis.call('GET', prefix .. 'current_conversations') or '0')
local max = tonumber(redis.call('GET', prefix .. 'max_conversations') or '0')
if redis.call('GET', prefix .. 'state') == 'online'
    and redis.call('GET', prefix .. 'status') == 'available' and current < max then
    for _, skill in ipairs(redis.call('SMEMBERS', prefix .. 'skills')) do
        redis.call('SADD', 'available_agents:' .. skill, agent_id)
    end
end
return {'OK', tostring(assigned)}
