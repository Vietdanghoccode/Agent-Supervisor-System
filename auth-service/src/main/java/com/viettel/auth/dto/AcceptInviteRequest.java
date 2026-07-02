package com.viettel.auth.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
public record AcceptInviteRequest(@NotBlank String token,
                                  @NotBlank @Size(min=8,max=100) String password,
                                  @NotBlank @Size(max=120) String displayName,
                                  @Min(1) Integer maxConversations,
                                  Set<@NotBlank @Size(max=100) String> skills,
                                  Set<@NotBlank @Size(max=50) String> channels) {}
