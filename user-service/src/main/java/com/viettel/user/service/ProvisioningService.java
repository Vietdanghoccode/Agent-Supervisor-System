package com.viettel.user.service;

import com.viettel.user.dto.ProvisionDtos.*;
import com.viettel.user.entity.Invite;
import com.viettel.user.entity.SupervisorAssignment;
import com.viettel.user.entity.User;
import com.viettel.user.entity.UserProfile;
import com.viettel.user.repository.InviteRepository;
import com.viettel.user.repository.SupervisorAssignmentRepository;
import com.viettel.user.repository.UserProfileRepository;
import com.viettel.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;

@Service
public class ProvisioningService {
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final InviteRepository invites;
    private final SupervisorAssignmentRepository assignments;

    public ProvisioningService(UserRepository users, UserProfileRepository profiles, InviteRepository invites,
                               SupervisorAssignmentRepository assignments) {
        this.users = users; this.profiles = profiles; this.invites = invites; this.assignments = assignments;
    }

    @Transactional
    public ProvisionedUser createCustomer(CustomerRequest request) {
        return createUser(request.email(), request.passwordHash(), request.displayName(), 1);
    }

    @Transactional
    public InviteView createInvite(CreateInviteRequest request) {
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email).isPresent()
                || invites.existsByEmailIgnoreCaseAndRoleIdAndStatus(email, request.roleId(), Invite.Status.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already has an account or pending invite");
        }
        Invite invite = Invite.builder().email(email).roleId(request.roleId()).createdBy(request.createdBy())
                .tokenHash(request.tokenHash()).expiresAt(request.expiresAt()).status(Invite.Status.PENDING)
                .teams(new HashSet<>(request.teams())).permissions(new HashSet<>(request.permissions())).build();
        return view(invites.save(invite));
    }

    @Transactional
    public InviteView resend(long id, ResendInviteRequest request) {
        Invite invite = invites.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (invite.getStatus() != Invite.Status.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending invites can be resent");
        }
        invite.setTokenHash(request.tokenHash()); invite.setExpiresAt(request.expiresAt());
        return view(invite);
    }

    @Transactional
    public InviteView claim(ClaimInviteRequest request) {
        Invite invite = invites.findByTokenHashForUpdate(request.tokenHash())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid invite token"));
        if (invite.getStatus() != Invite.Status.PENDING || !invite.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite is expired or already used");
        }
        invite.setStatus(Invite.Status.CLAIMED);
        return view(invite);
    }

    @Transactional
    public ProvisionedUser provision(long inviteId, ProvisionInviteRequest request) {
        Invite invite = invites.findById(inviteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (invite.getStatus() != Invite.Status.CLAIMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invite has not been claimed");
        }
        ProvisionedUser result = createUser(invite.getEmail(), request.passwordHash(), request.displayName(), invite.getRoleId());
        if (invite.getRoleId() == 3) {
            assignments.save(SupervisorAssignment.builder().userId(result.id())
                    .teams(new HashSet<>(invite.getTeams())).permissions(new HashSet<>(invite.getPermissions())).build());
        }
        invite.setStatus(Invite.Status.CONSUMED); invite.setConsumedAt(Instant.now());
        return result;
    }

    @Transactional
    public void releaseClaim(long inviteId) {
        Invite invite = invites.findById(inviteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (invite.getStatus() == Invite.Status.CLAIMED) invite.setStatus(Invite.Status.PENDING);
    }

    @Transactional
    public void rollback(long inviteId, long userId) {
        Invite invite = invites.findById(inviteId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assignments.deleteById(userId); profiles.deleteById(userId); users.deleteById(userId);
        invite.setStatus(Invite.Status.PENDING); invite.setConsumedAt(null);
    }

    private ProvisionedUser createUser(String rawEmail, String passwordHash, String displayName, int roleId) {
        String email = normalizeEmail(rawEmail);
        if (users.findByEmail(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        try {
            User user = users.save(User.builder().email(email).passwordHash(passwordHash).roleId(roleId).status("ACTIVE").build());
            profiles.save(UserProfile.builder().userId(user.getId()).displayName(displayName.trim()).build());
            return new ProvisionedUser(user.getId(), email, roleId, "ACTIVE", displayName.trim());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists", exception);
        }
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(); }
    private InviteView view(Invite i) {
        return new InviteView(i.getId(), i.getEmail(), i.getRoleId(), i.getTeams(), i.getPermissions(),
                i.getExpiresAt(), i.getStatus().name());
    }
}
