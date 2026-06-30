package com.viettel.agent.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AgentProfileRequest(
        @Min(1) int maxConversations,
        @NotEmpty Set<@NotBlank @Size(max = 100) String> skills,
        @NotNull Set<@NotBlank @Size(max = 100) String> teams,
        @NotNull Set<@NotBlank @Size(max = 50) String> channels) {
}
