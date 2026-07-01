package com.viettel.agent.service;

import com.viettel.agent.api.AgentStateResponse;
import com.viettel.agent.exception.AgentNotFoundException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentStateStore {
    private final StringRedisTemplate redis;

    public AgentStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public AgentStateResponse get(long agentId) {
        String prefix = "agent:" + agentId + ":";
        List<String> values = redis.opsForValue().multiGet(List.of(
                prefix + "state",
                prefix + "status",
                prefix + "current_conversations",
                prefix + "max_conversations"));
        if (values == null || values.size() != 4 || values.get(3) == null) {
            throw new AgentNotFoundException("Agent profile " + agentId + " does not exist");
        }
        return new AgentStateResponse(
                agentId,
                values.get(0),
                values.get(1),
                Integer.parseInt(values.get(2)),
                Integer.parseInt(values.get(3)));
    }
}
