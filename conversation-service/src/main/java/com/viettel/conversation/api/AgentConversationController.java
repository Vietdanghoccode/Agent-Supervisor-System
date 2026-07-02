package com.viettel.conversation.api;

import com.viettel.conversation.service.ConversationMessagingService;
import com.viettel.conversation.service.RequestIdentity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conversation/agent")
public class AgentConversationController {
    private final ConversationMessagingService messagingService;

    public AgentConversationController(ConversationMessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/me")
    public AgentConversationPageResponse mine(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return messagingService.agentInbox(RequestIdentity.from(userId, role), cursor, limit);
    }
}
