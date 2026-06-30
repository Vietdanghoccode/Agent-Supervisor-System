local conversation_id = ARGV[1]
local reservation_key = 'reservation:' .. conversation_id
local owner_key = 'reservation_owner:' .. conversation_id
local assignment_key = 'assignment:' .. conversation_id

local assigned_agent = redis.call('GET', assignment_key)
if assigned_agent then
    return {'CONFIRMED', assigned_agent}
end

local reserved_agent = redis.call('GET', reservation_key)
if not reserved_agent then
    return {'NOT_FOUND'}
end

redis.call('SET', assignment_key, reserved_agent)
redis.call('DEL', reservation_key, owner_key)
return {'CONFIRMED', reserved_agent}
