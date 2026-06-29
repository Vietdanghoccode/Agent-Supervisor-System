package com.viettel.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @NotNull @Positive Long customerId,
        @NotBlank @Size(max = 10_000) String message,
        @NotBlank @Size(max = 50) String channel,
        @Size(max = 100) String skill
) {
}
