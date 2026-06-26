package com.viettel.auth.service;

import com.viettel.auth.client.UserClient;
import com.viettel.auth.dto.AuthRequest;
import com.viettel.auth.dto.AuthResponse;
import com.viettel.auth.dto.UserDto;
import com.viettel.auth.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserClient userClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
