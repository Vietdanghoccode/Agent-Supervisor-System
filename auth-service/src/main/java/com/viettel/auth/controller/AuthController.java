package com.viettel.auth.controller;

import com.viettel.auth.dto.AuthRequest;
import com.viettel.auth.dto.AuthResponse;
import com.viettel.auth.dto.RefreshTokenRequest;
import com.viettel.auth.service.AuthService;
import com.viettel.auth.dto.AcceptInviteRequest;
import com.viettel.auth.dto.CreateInviteRequest;
import com.viettel.auth.dto.InviteResponse;
import com.viettel.auth.dto.SignupCustomerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/signup/customer")
    public ResponseEntity<AuthResponse> signupCustomer(@Valid @RequestBody SignupCustomerRequest request) {
        return ResponseEntity.status(201).body(authService.signupCustomer(request));
    }

    @PostMapping("/invites")
    public ResponseEntity<InviteResponse> createInvite(@Valid @RequestBody CreateInviteRequest request,
                                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.status(201).body(authService.createInvite(request, bearer(authorization)));
    }

    @PostMapping("/invites/{id}/resend")
    public InviteResponse resendInvite(@PathVariable long id,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return authService.resendInvite(id, bearer(authorization));
    }

    @PostMapping("/invites/accept")
    public AuthResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        return authService.acceptInvite(request);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/validate")
    public ResponseEntity<String> validateToken(@RequestParam("token") String token) {
        boolean isValid = authService.validateToken(token);
        if (isValid) {
            return ResponseEntity.ok("Token is valid");
        } else {
            return ResponseEntity.status(401).body("Token is invalid or expired");
        }
    }

    private String bearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
    }
}
