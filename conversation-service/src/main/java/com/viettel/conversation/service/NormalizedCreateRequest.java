package com.viettel.conversation.service;

import com.viettel.conversation.api.CreateConversationRequest;

record NormalizedCreateRequest(Long customerId, String clientMessageId, String message, String channel, String skill) {
    static NormalizedCreateRequest from(long customerId, CreateConversationRequest request) {
        String normalizedSkill = request.skill() == null ? null : request.skill().trim();
        if (normalizedSkill != null && normalizedSkill.isEmpty()) {
            normalizedSkill = null;
        }
        return new NormalizedCreateRequest(
                customerId,
                request.clientMessageId().trim(),
                request.message(),
                request.channel().trim(),
                normalizedSkill
        );
    }
}
