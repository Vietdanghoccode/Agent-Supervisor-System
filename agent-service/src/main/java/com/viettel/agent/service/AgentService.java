package com.viettel.agent.service;

import com.viettel.agent.api.AgentProfileRequest;
import com.viettel.agent.api.AgentStateResponse;
import com.viettel.agent.api.ConfirmationResponse;
import com.viettel.agent.api.ReservationResponse;
import com.viettel.agent.api.ReserveRequest;
import com.viettel.agent.config.ReservationProperties;
import com.viettel.agent.config.WaitingQueueProperties;
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
    private final long waitingRetentionSeconds;

    public AgentService(AgentScripts scripts, AgentStateStore stateStore, ReservationProperties properties,
                        WaitingQueueProperties waitingQueueProperties) {
        this.scripts = scripts;
        this.stateStore = stateStore;
        this.reservationTtlSeconds = Math.max(1, properties.ttl().toSeconds());
        this.waitingRetentionSeconds = Math.max(1, waitingQueueProperties.retention().toSeconds());
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
        dispatch(agentId);
        return stateStore.get(agentId);
    }

    public AgentStateResponse online(long agentId) {
        requireProfile(scripts.online(agentId), agentId);
        dispatch(agentId);
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
        dispatch(agentId);
        return stateStore.get(agentId);
    }

    public ReservationResponse reserve(ReserveRequest request) {
        String skill = request.skill().trim();
        List<String> result = scripts.reserve(
                request.conversationId().toString(), skill, reservationTtlSeconds,
                System.currentTimeMillis());
        if ("SKILL_CONFLICT".equals(result.get(0))) {
            throw new AgentConflictException("conversationId was already submitted with a different skill");
        }
        return new ReservationResponse(
                request.conversationId(),
                result.get(1).isBlank() ? null : Long.parseLong(result.get(1)),
                result.get(0),
                Long.parseLong(result.get(2)));
    }

    public ConfirmationResponse confirm(UUID conversationId) {
        List<String> result = scripts.confirm(conversationId.toString(), waitingRetentionSeconds);
        if ("NOT_FOUND".equals(result.get(0))) {
            throw new AgentConflictException("Reservation has expired or does not exist");
        }
        return new ConfirmationResponse(conversationId, Long.parseLong(result.get(1)), "CONFIRMED");
    }

    public AgentStateResponse release(long agentId, UUID conversationId) {
        List<String> result = scripts.release(agentId, conversationId.toString(), waitingRetentionSeconds);
        if ("PROFILE_NOT_FOUND".equals(result.get(0))) {
            throw new AgentNotFoundException("Agent profile " + agentId + " does not exist");
        }
        if ("OWNER_MISMATCH".equals(result.get(0))) {
            throw new AgentConflictException("Conversation belongs to agent " + result.get(1));
        }
        dispatch(agentId);
        return stateStore.get(agentId);
    }

    public void releaseExpired(String conversationId) {
        List<String> result = scripts.expireReservation(conversationId, waitingRetentionSeconds);
        if (result.size() > 1 && "RELEASED".equals(result.get(0))) {
            dispatch(Long.parseLong(result.get(1)));
        }
    }

    public ReservationResponse reservation(UUID conversationId) {
        List<String> result = scripts.requestStatus(conversationId.toString());
        if ("NOT_FOUND".equals(result.get(0))) {
            throw new AgentNotFoundException("Reservation " + conversationId + " does not exist");
        }
        return new ReservationResponse(conversationId,
                result.get(1).isBlank() ? null : Long.parseLong(result.get(1)),
                result.get(0), Long.parseLong(result.get(2)));
    }

    public ReservationResponse cancel(UUID conversationId) {
        List<String> result = scripts.cancelRequest(conversationId.toString(), waitingRetentionSeconds);
        if ("NOT_FOUND".equals(result.get(0))) {
            throw new AgentNotFoundException("Reservation " + conversationId + " does not exist");
        }
        if ("CONFLICT".equals(result.get(0))) {
            throw new AgentConflictException("Only WAITING requests can be cancelled; current status is " + result.get(1));
        }
        return reservation(conversationId);
    }

    private void dispatch(long agentId) {
        scripts.dispatchAgent(agentId, reservationTtlSeconds, System.currentTimeMillis());
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
