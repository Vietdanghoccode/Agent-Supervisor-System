package com.viettel.agent.api;

import com.viettel.agent.service.AgentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
public class ReservationController {
    private final AgentService agentService;

    public ReservationController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/{conversationId}/confirm")
    public ConfirmationResponse confirm(@PathVariable UUID conversationId) {
        return agentService.confirm(conversationId);
    }

    @GetMapping("/{conversationId}")
    public ReservationResponse get(@PathVariable UUID conversationId) {
        return agentService.reservation(conversationId);
    }

    @DeleteMapping("/{conversationId}")
    public ReservationResponse cancel(@PathVariable UUID conversationId) {
        return agentService.cancel(conversationId);
    }
}
