local conversation_id = ARGV[1]
local retention = tonumber(ARGV[2])
local request_key = 'request:' .. conversation_id
local status = redis.call('HGET', request_key, 'status')
if not status then return {'NOT_FOUND'} end
if status == 'CANCELLED' then return {'CANCELLED'} end
if status ~= 'WAITING' then return {'CONFLICT', status} end
local skill = redis.call('HGET', request_key, 'skill')
redis.call('ZREM', 'waiting_requests:' .. skill, conversation_id)
redis.call('HSET', request_key, 'status', 'CANCELLED')
redis.call('EXPIRE', request_key, retention)
return {'CANCELLED'}
