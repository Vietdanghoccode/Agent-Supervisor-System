package com.viettel.conversation.service;

import com.viettel.conversation.api.ConversationResponse;

public record CreateConversationResult(ConversationResponse response, boolean replayed) {
}
