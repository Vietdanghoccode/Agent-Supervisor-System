package com.viettel.agent.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseRequest(@NotNull UUID conversationId) {
}
