package com.viettel.conversation.service;

import com.viettel.conversation.domain.SenderType;
import com.viettel.conversation.exception.ForbiddenException;

public record RequestIdentity(long userId, String role) {
    public static RequestIdentity from(String userId, String role) {
        try {
            return new RequestIdentity(Long.parseLong(userId), role == null ? "" : role.toLowerCase());
        } catch (NumberFormatException exception) {
            throw new ForbiddenException("Invalid gateway identity");
        }
    }

    public SenderType senderType() {
        return switch (role) {
            case "customer" -> SenderType.CUSTOMER;
            case "agent" -> SenderType.AGENT;
            default -> throw new ForbiddenException("Role is not allowed for conversation operations");
        };
    }
}
