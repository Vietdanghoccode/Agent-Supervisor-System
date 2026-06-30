package com.viettel.agent.service;

import com.viettel.agent.api.AgentProfileRequest;
import com.viettel.agent.api.AgentStateResponse;
import com.viettel.agent.api.ConfirmationResponse;
import com.viettel.agent.api.ReservationResponse;
import com.viettel.agent.api.ReserveRequest;
import com.viettel.agent.config.ReservationProperties;
import com.viettel.agent.exception.AgentConflictException;
import com.viettel.agent.exception.AgentNotFoundException;
import com.viettel.agent.redis.AgentScripts;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentService {
    private final AgentScripts scripts;
    private final AgentStateStore stateStore;
    private final long reservationTtlSeconds;

    public AgentService(AgentScripts scripts, AgentStateStore stateStore, ReservationProperties properties) {
        this.scripts = scripts;
        this.stateStore = stateStore;
        this.reservationTtlSeconds = Math.max(1, properties.ttl().toSeconds());
    }

    public AgentStateResponse updateProfile(long agentId, AgentProfileRequest request) {
        List<String> args = new ArrayList<>();
        args.add(Long.toString(agentId));
        args.add(Integer.toString(request.maxConversations()));
        appendSet(args, request.skills());
        appendSet(args, request.teams());
        appendSet(args, request.channels());
        List<String> result = scripts.updateProfile(args);
        if ("MAX_BELOW_CURRENT".equals(result.get(0))) {
            throw new AgentConflictException(
                    "maxConversations cannot be lower than currentConversations (" + result.get(1) + ")");
        }
        return stateStore.get(agentId);
    }

    public AgentStateResponse online(long agentId) {
        requireProfile(scripts.online(agentId), agentId);
        return stateStore.get(agentId);
    }

    public AgentStateResponse offline(long agentId) {
        requireProfile(scripts.offline(agentId), agentId);
        return stateStore.get(agentId);
    }

    public AgentStateResponse requestBreak(long agentId) {
        requireOnline(scripts.requestBreak(agentId), agentId, "request break");
        return stateStore.get(agentId);
    }

    public AgentStateResponse available(long agentId) {
        requireOnline(scripts.available(agentId), agentId, "become available");
        return stateStore.get(agentId);
    }

    public ReservationResponse reserve(ReserveRequest request) {
        String skill = request.skill().trim();
        List<String> result = scripts.reserve(
                request.conversationId().toString(), skill, reservationTtlSeconds);
        if ("NO_AGENT".equals(result.get(0))) {
            throw new AgentConflictException("No available agent for skill " + skill);
        }
        if ("RECOVERY_PENDING".equals(result.get(0))) {
            throw new AgentConflictException("Expired reservation is still being recovered; retry shortly");
        }
        return new ReservationResponse(
                request.conversationId(),
                Long.parseLong(result.get(1)),
                result.get(0),
                Long.parseLong(result.get(2)));
    }

    public ConfirmationResponse confirm(UUID conversationId) {
        List<String> result = scripts.confirm(conversationId.toString());
        if ("NOT_FOUND".equals(result.get(0))) {
            throw new AgentConflictException("Reservation has expired or does not exist");
        }
        return new ConfirmationResponse(conversationId, Long.parseLong(result.get(1)), "CONFIRMED");
    }

    public AgentStateResponse release(long agentId, UUID conversationId) {
        List<String> result = scripts.release(agentId, conversationId.toString());
        if ("PROFILE_NOT_FOUND".equals(result.get(0))) {
            throw new AgentNotFoundException("Agent profile " + agentId + " does not exist");
        }
        if ("OWNER_MISMATCH".equals(result.get(0))) {
            throw new AgentConflictException("Conversation belongs to agent " + result.get(1));
        }
        return stateStore.get(agentId);
    }

    public void releaseExpired(String conversationId) {
        String owner = stateStore.reservationOwner(conversationId);
        if (owner != null) {
            scripts.release(Long.parseLong(owner), conversationId);
        }
    }

    private void requireProfile(List<String> result, long agentId) {
        if ("PROFILE_NOT_FOUND".equals(result.get(0))) {
            throw new AgentNotFoundException("Agent profile " + agentId + " does not exist");
        }
    }

    private void requireOnline(List<String> result, long agentId, String action) {
        requireProfile(result, agentId);
        if ("OFFLINE".equals(result.get(0))) {
            throw new AgentConflictException("Agent must be online to " + action);
        }
    }

    private static void appendSet(List<String> args, Set<String> values) {
        List<String> normalized = values.stream()
                .map(String::trim)
                .sorted(Comparator.naturalOrder())
                .toList();
        args.add(Integer.toString(normalized.size()));
        args.addAll(normalized);
    }
}
