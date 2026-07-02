package com.viettel.user.controller;

import com.viettel.user.dto.UserDto;
import com.viettel.user.entity.User;
import com.viettel.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import com.viettel.user.dto.ProvisionDtos.*;
import com.viettel.user.service.ProvisioningService;

@RestController
@RequestMapping("/api/user/internal")
public class InternalUserController {

    @Autowired
    private UserRepository userRepository;

    private final ProvisioningService provisioningService;

    public InternalUserController(ProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @GetMapping("/users/by-email")
    public ResponseEntity<UserDto> getUserByEmail(@RequestParam String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            UserDto dto = UserDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .passwordHash(user.getPasswordHash())
                    .roleId(user.getRoleId())
                    .status(user.getStatus())
                    .build();
            return ResponseEntity.ok(dto);
        }
        
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/customers")
    public ProvisionedUser createCustomer(@RequestBody CustomerRequest request) {
        return provisioningService.createCustomer(request);
    }

    @PostMapping("/invites")
    public InviteView createInvite(@RequestBody CreateInviteRequest request) { return provisioningService.createInvite(request); }

    @PutMapping("/invites/{id}/resend")
    public InviteView resend(@PathVariable long id, @RequestBody ResendInviteRequest request) {
        return provisioningService.resend(id, request);
    }

    @PostMapping("/invites/claim")
    public InviteView claim(@RequestBody ClaimInviteRequest request) { return provisioningService.claim(request); }

    @PostMapping("/invites/{id}/provision")
    public ProvisionedUser provision(@PathVariable long id, @RequestBody ProvisionInviteRequest request) {
        return provisioningService.provision(id, request);
    }

    @PutMapping("/invites/{id}/release")
    public ResponseEntity<Void> release(@PathVariable long id) {
        provisioningService.releaseClaim(id); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/invites/{inviteId}/users/{userId}")
    public ResponseEntity<Void> rollback(@PathVariable long inviteId, @PathVariable long userId) {
        provisioningService.rollback(inviteId, userId); return ResponseEntity.noContent().build();
    }
}
