package com.viettel.user.dto;

import java.time.Instant;
import java.util.Set;

public final class ProvisionDtos {
    private ProvisionDtos() {}
    public record CustomerRequest(String email, String passwordHash, String displayName) {}
    public record CreateInviteRequest(String email, Integer roleId, Set<String> teams, Set<String> permissions,
                                      Long createdBy, String tokenHash, Instant expiresAt) {}
    public record ResendInviteRequest(String tokenHash, Instant expiresAt) {}
    public record ClaimInviteRequest(String tokenHash) {}
    public record ProvisionInviteRequest(String passwordHash, String displayName) {}
    public record ProvisionedUser(Long id, String email, Integer roleId, String status, String displayName) {}
    public record InviteView(Long id, String email, Integer roleId, Set<String> teams, Set<String> permissions,
                             Instant expiresAt, String status) {}
}
