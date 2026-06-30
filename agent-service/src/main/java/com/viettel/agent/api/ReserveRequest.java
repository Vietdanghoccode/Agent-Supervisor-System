package com.viettel.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReserveRequest(
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 100) String skill) {
}
