package com.viettel.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 100) String clientMessageId,
        @NotBlank @Size(max = 10_000) String content,
        @Size(max = 50) String contentType
) {
}
