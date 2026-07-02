package com.viettel.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
public record CreateInviteRequest(@NotBlank @Email String email, @NotNull Role role,
                                  @NotEmpty Set<@NotBlank @Size(max=100) String> teams,
                                  @NotNull Set<@NotBlank @Size(max=100) String> permissions) {
    public enum Role { AGENT, SUPERVISOR }
}
