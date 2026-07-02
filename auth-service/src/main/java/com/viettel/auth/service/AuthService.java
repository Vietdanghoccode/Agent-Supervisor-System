package com.viettel.auth.service;

import com.viettel.auth.client.UserClient;
import com.viettel.auth.dto.AuthRequest;
import com.viettel.auth.dto.AuthResponse;
import com.viettel.auth.dto.UserDto;
import com.viettel.auth.util.JwtUtil;
import com.viettel.auth.client.AgentClient;
import com.viettel.auth.client.EmailClient;
import com.viettel.auth.dto.AcceptInviteRequest;
import com.viettel.auth.dto.CreateInviteRequest;
import com.viettel.auth.dto.InviteResponse;
import com.viettel.auth.dto.SignupCustomerRequest;
import com.viettel.auth.dto.ProvisioningDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

@Service
public class AuthService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserClient userClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final AgentClient agentClient;
    private final EmailClient emailClient;
    private final Duration inviteTtl;
    private final String acceptBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AgentClient agentClient, EmailClient emailClient,
                       @Value("${app.invite.ttl}") Duration inviteTtl,
                       @Value("${app.invite.accept-base-url}") String acceptBaseUrl) {
        this.agentClient = agentClient; this.emailClient = emailClient;
        this.inviteTtl = inviteTtl; this.acceptBaseUrl = acceptBaseUrl;
    }

    public AuthResponse signupCustomer(SignupCustomerRequest request) {
        ProvisionedUser user = userClient.createCustomer(new CustomerProvision(request.email(),
                passwordEncoder.encode(request.password()), request.displayName()));
        return tokens(user);
    }

    public InviteResponse createInvite(CreateInviteRequest request, String bearerToken) {
        requireSupervisor(bearerToken);
        if (request.role() == CreateInviteRequest.Role.AGENT && !request.permissions().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent invite must not contain permissions");
        }
        String raw = newToken(); Instant expiresAt = Instant.now().plus(inviteTtl);
        int roleId = request.role() == CreateInviteRequest.Role.AGENT ? 2 : 3;
        InviteView invite = userClient.createInvite(new InviteProvision(request.email(), roleId, request.teams(),
                request.permissions(), jwtUtil.extractUserId(bearerToken), hash(raw), expiresAt));
        sendInvite(invite, raw, jwtUtil.extractUsername(bearerToken));
        return response(invite);
    }

    public InviteResponse resendInvite(long id, String bearerToken) {
        requireSupervisor(bearerToken);
        String raw = newToken(); Instant expiresAt = Instant.now().plus(inviteTtl);
        InviteView invite = userClient.resend(id, new ResendProvision(hash(raw), expiresAt));
        sendInvite(invite, raw, jwtUtil.extractUsername(bearerToken));
        return response(invite);
    }

    public AuthResponse acceptInvite(AcceptInviteRequest request) {
        InviteView invite = userClient.claim(new ClaimProvision(hash(request.token())));
        try {
            if (invite.roleId() == 2 && (request.maxConversations() == null || request.maxConversations() < 1
                    || request.skills() == null || request.skills().isEmpty() || request.channels() == null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Agent invite requires maxConversations, non-empty skills and channels");
            }
            ProvisionedUser user = userClient.provision(invite.id(),
                    new CompleteProvision(passwordEncoder.encode(request.password()), request.displayName()));
            if (invite.roleId() == 2) {
                try {
                    agentClient.createProfile(user.id(), new AgentProfile(request.maxConversations(), request.skills(),
                            invite.teams(), request.channels()));
                } catch (RuntimeException failure) {
                    userClient.rollback(invite.id(), user.id());
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not create agent profile", failure);
                }
            }
            return tokens(user);
        } catch (RuntimeException failure) {
            try { userClient.releaseClaim(invite.id()); } catch (RuntimeException ignored) { }
            throw failure;
        }
    }

    private void requireSupervisor(String token) {
        if (token == null || !jwtUtil.validateToken(token) || !"supervisor".equalsIgnoreCase(jwtUtil.extractRole(token)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Supervisor role required");
    }
    private void sendInvite(InviteView invite, String raw, String inviter) {
        String separator = acceptBaseUrl.contains("?") ? "&" : "?";
        String url = acceptBaseUrl + separator + "token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);
        emailClient.sendInvitation(new InvitationEmail(invite.email(), role(invite.roleId()), inviter, url, invite.expiresAt()));
    }
    private AuthResponse tokens(ProvisionedUser user) {
        String role = mapRoleIdToRole(user.roleId());
        return AuthResponse.builder().accessToken(jwtUtil.generateAccessToken(user.email(), user.id(), role))
                .refreshToken(jwtUtil.generateRefreshToken(user.email(), user.id(), role)).build();
    }
    private InviteResponse response(InviteView i) { return new InviteResponse(i.id(), i.email(), role(i.roleId()), i.expiresAt(), i.status()); }
    private String role(int id) { return id == 2 ? "AGENT" : "SUPERVISOR"; }
    private String newToken() { byte[] bytes = new byte[32]; secureRandom.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String token) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public AuthResponse login(AuthRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new RuntimeException("Invalid credentials");
        }

        UserDto user = userClient.getUserByEmail(request.getUsername());
        
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("User not found or inactive");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }
        
        String role = mapRoleIdToRole(user.getRoleId());

        String accessToken = jwtUtil.generateAccessToken(request.getUsername(), user.getId(), role);
        String refreshToken = jwtUtil.generateRefreshToken(request.getUsername(), user.getId(), role);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (jwtUtil.validateToken(refreshToken)) {
            String username = jwtUtil.extractUsername(refreshToken);
            
            // Re-fetch user to make sure they are still active and get current role
            UserDto user = userClient.getUserByEmail(username);
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                throw new RuntimeException("User not found or inactive");
            }
            
            String role = mapRoleIdToRole(user.getRoleId());
            
            String newAccessToken = jwtUtil.generateAccessToken(username, user.getId(), role);
            String newRefreshToken = jwtUtil.generateRefreshToken(username, user.getId(), role);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build();
        } else {
            throw new RuntimeException("Invalid or expired refresh token");
        }
    }

    public boolean validateToken(String token) {
        return jwtUtil.validateToken(token);
    }
    
    private String mapRoleIdToRole(Integer roleId) {
        if (roleId == null) return "customer";
        return switch (roleId) {
            case 1 -> "customer";
            case 2 -> "agent";
            case 3 -> "supervisor";
            default -> "customer";
        };
    }
}
