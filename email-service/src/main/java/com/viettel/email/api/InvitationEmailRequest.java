package com.viettel.email.api;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public record InvitationEmailRequest(@NotBlank @Email String to,@NotBlank String role,@NotBlank String inviter,
                                     @NotBlank String acceptUrl,@NotNull @Future Instant expiresAt) {}
