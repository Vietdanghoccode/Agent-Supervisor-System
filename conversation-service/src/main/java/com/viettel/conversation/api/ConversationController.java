package com.viettel.conversation.api;

import com.viettel.conversation.service.ConversationService;
import com.viettel.conversation.service.CreateConversationResult;
import com.viettel.conversation.service.ConversationMessagingService;
import com.viettel.conversation.service.MessageSendResult;
import com.viettel.conversation.service.RequestIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationController {
    private final ConversationService conversationService;
    private final ConversationMessagingService messagingService;

    public ConversationController(ConversationService conversationService,
                                  ConversationMessagingService messagingService) {
        this.conversationService = conversationService;
        this.messagingService = messagingService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateConversationRequest request,
            HttpServletRequest httpRequest) {
        CreateConversationResult result = conversationService.create(idempotencyKey,
                RequestIdentity.from(userId, role), request);
        return ResponseEntity.created(URI.create(httpRequest.getRequestURI() + "/" + result.response().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/{conversationId}/close")
    public ConversationResponse close(@PathVariable UUID conversationId,
                                      @RequestHeader("X-User-Id") String userId,
                                      @RequestHeader("X-User-Role") String role) {
        return conversationService.close(conversationId, RequestIdentity.from(userId, role));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID conversationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest) {
        MessageSendResult result = messagingService.send(conversationId,
                RequestIdentity.from(userId, role), request);
        ResponseEntity.BodyBuilder response = result.replayed()
                ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create(httpRequest.getRequestURI() + "/" + result.response().id()));
        return response.header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.response());
    }

    @GetMapping("/{conversationId}/messages")
    public MessagePageResponse listMessages(
            @PathVariable UUID conversationId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(required = false) Long beforeSeq,
            @RequestParam(defaultValue = "50") int limit) {
        return messagingService.listMessages(conversationId, RequestIdentity.from(userId, role),
                afterSeq, beforeSeq, limit);
    }
}
