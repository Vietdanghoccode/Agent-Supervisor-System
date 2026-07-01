local conversation_id = ARGV[1]
local request_key = 'request:' .. conversation_id
local status = redis.call('HGET', request_key, 'status')
if not status then return {'NOT_FOUND'} end
local agent_id = redis.call('HGET', request_key, 'agent_id') or ''
local ttl = redis.call('TTL', 'reservation:' .. conversation_id)
return {status, agent_id, tostring(math.max(ttl, 0))}
