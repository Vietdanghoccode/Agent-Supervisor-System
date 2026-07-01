package com.viettel.agent.redis;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class AgentScripts {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> updateProfile = script("redis/update-profile.lua");
    private final DefaultRedisScript<List> online = script("redis/online.lua");
    private final DefaultRedisScript<List> offline = script("redis/offline.lua");
    private final DefaultRedisScript<List> requestBreak = script("redis/break.lua");
    private final DefaultRedisScript<List> available = script("redis/available.lua");
    private final DefaultRedisScript<List> reserve = script("redis/reserve.lua");
    private final DefaultRedisScript<List> confirm = script("redis/confirm.lua");
    private final DefaultRedisScript<List> release = script("redis/release.lua");
    private final DefaultRedisScript<List> dispatchAgent = script("redis/dispatch-agent.lua");
    private final DefaultRedisScript<List> requestStatus = script("redis/request-status.lua");
    private final DefaultRedisScript<List> cancelRequest = script("redis/cancel-request.lua");
    private final DefaultRedisScript<List> expireReservation = script("redis/expire-reservation.lua");

    public AgentScripts(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<String> updateProfile(List<String> args) {
        return execute(updateProfile, args);
    }

    public List<String> online(long agentId) {
        return execute(online, List.of(Long.toString(agentId)));
    }

    public List<String> offline(long agentId) {
        return execute(offline, List.of(Long.toString(agentId)));
    }

    public List<String> requestBreak(long agentId) {
        return execute(requestBreak, List.of(Long.toString(agentId)));
    }

    public List<String> available(long agentId) {
        return execute(available, List.of(Long.toString(agentId)));
    }

    public List<String> reserve(String conversationId, String skill, long ttlSeconds, long nowMillis) {
        return execute(reserve, List.of(conversationId, skill, Long.toString(ttlSeconds), Long.toString(nowMillis)));
    }

    public List<String> confirm(String conversationId, long retentionSeconds) {
        return execute(confirm, List.of(conversationId, Long.toString(retentionSeconds)));
    }

    public List<String> release(long agentId, String conversationId, long retentionSeconds) {
        return execute(release, List.of(Long.toString(agentId), conversationId, Long.toString(retentionSeconds)));
    }

    public List<String> dispatchAgent(long agentId, long ttlSeconds, long nowMillis) {
        return execute(dispatchAgent, List.of(
                Long.toString(agentId), Long.toString(ttlSeconds), Long.toString(nowMillis)));
    }

    public List<String> requestStatus(String conversationId) {
        return execute(requestStatus, List.of(conversationId));
    }

    public List<String> cancelRequest(String conversationId, long retentionSeconds) {
        return execute(cancelRequest, List.of(conversationId, Long.toString(retentionSeconds)));
    }

    public List<String> expireReservation(String conversationId, long retentionSeconds) {
        return execute(expireReservation, List.of(conversationId, Long.toString(retentionSeconds)));
    }

    private List<String> execute(DefaultRedisScript<List> script, List<String> args) {
        List<?> result = redis.execute(script, Collections.emptyList(), args.toArray());
        if (result == null) {
            throw new IllegalStateException("Redis script returned no result");
        }
        return result.stream().map(String::valueOf).toList();
    }

    private static DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}
