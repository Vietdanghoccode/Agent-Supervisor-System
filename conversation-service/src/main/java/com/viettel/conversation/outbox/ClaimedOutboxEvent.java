package com.viettel.conversation.outbox;

import java.util.UUID;

record ClaimedOutboxEvent(
        UUID id,
        UUID aggregateId,
        String eventType,
        String payload,
        int attemptCount) {
}
