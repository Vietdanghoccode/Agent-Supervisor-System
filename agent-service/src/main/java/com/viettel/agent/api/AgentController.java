package com.viettel.agent.api;

import com.viettel.agent.service.AgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@Validated
@RestController
@RequestMapping("/agents")
public class AgentController {
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PutMapping("/{agentId}/profile")
    public AgentStateResponse updateProfile(
            @PathVariable @Positive long agentId,
            @Valid @RequestBody AgentProfileRequest request) {
        return agentService.updateProfile(agentId, request);
    }

    @PostMapping("/{agentId}/online")
    public AgentStateResponse online(@PathVariable @Positive long agentId) {
        return agentService.online(agentId);
    }

    @PostMapping("/{agentId}/offline")
    public AgentStateResponse offline(@PathVariable @Positive long agentId) {
        return agentService.offline(agentId);
    }

    @PostMapping("/{agentId}/break")
    public AgentStateResponse requestBreak(@PathVariable @Positive long agentId) {
        return agentService.requestBreak(agentId);
    }

    @PostMapping("/{agentId}/available")
    public AgentStateResponse available(@PathVariable @Positive long agentId) {
        return agentService.available(agentId);
    }

    @PostMapping("/reserve")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        ReservationResponse response = agentService.reserve(request);
        return "WAITING".equals(response.status())
                ? ResponseEntity.accepted().body(response)
                : ResponseEntity.ok(response);
    }

    @PostMapping("/{agentId}/release")
    public AgentStateResponse release(
            @PathVariable @Positive long agentId,
            @Valid @RequestBody ReleaseRequest request) {
        return agentService.release(agentId, request.conversationId());
    }
}
