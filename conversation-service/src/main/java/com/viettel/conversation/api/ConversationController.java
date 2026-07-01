package com.viettel.conversation.api;

import com.viettel.conversation.service.ConversationService;
import com.viettel.conversation.service.CreateConversationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateConversationRequest request,
            HttpServletRequest httpRequest) {
        CreateConversationResult result = conversationService.create(idempotencyKey, request);
        return ResponseEntity.created(URI.create(httpRequest.getRequestURI() + "/" + result.response().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.response());
    }

    @PostMapping("/{conversationId}/close")
    public ConversationResponse close(@PathVariable UUID conversationId) {
        return conversationService.close(conversationId);
    }
}
