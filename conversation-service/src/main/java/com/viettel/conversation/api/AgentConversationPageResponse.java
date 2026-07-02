package com.viettel.conversation.api;

import java.util.List;

public record AgentConversationPageResponse(
        List<AgentConversationResponse> conversations,
        String nextCursor,
        boolean hasMore
) {
}
