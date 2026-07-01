package com.viettel.conversation.assignment;

import com.viettel.conversation.domain.ConversationStatus;

import java.util.UUID;

record ClaimedConversation(UUID id, ConversationStatus originalStatus, String skill) {
}
