package com.viettel.auth.dto;
import java.time.Instant;
import java.util.Set;
public final class ProvisioningDtos {
 private ProvisioningDtos() {}
 public record CustomerProvision(String email,String passwordHash,String displayName) {}
 public record InviteProvision(String email,Integer roleId,Set<String> teams,Set<String> permissions,Long createdBy,String tokenHash,Instant expiresAt) {}
 public record ResendProvision(String tokenHash,Instant expiresAt) {}
 public record ClaimProvision(String tokenHash) {}
 public record CompleteProvision(String passwordHash,String displayName) {}
 public record ProvisionedUser(Long id,String email,Integer roleId,String status,String displayName) {}
 public record InviteView(Long id,String email,Integer roleId,Set<String> teams,Set<String> permissions,Instant expiresAt,String status) {}
 public record AgentProfile(int maxConversations,Set<String> skills,Set<String> teams,Set<String> channels) {}
 public record InvitationEmail(String to,String role,String inviter,String acceptUrl,Instant expiresAt) {}
}
