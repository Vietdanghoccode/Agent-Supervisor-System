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

    public List<String> reserve(String conversationId, String skill, long ttlSeconds) {
        return execute(reserve, List.of(conversationId, skill, Long.toString(ttlSeconds)));
    }

    public List<String> confirm(String conversationId) {
        return execute(confirm, List.of(conversationId));
    }

    public List<String> release(long agentId, String conversationId) {
        return execute(release, List.of(Long.toString(agentId), conversationId));
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
