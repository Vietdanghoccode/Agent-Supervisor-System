package com.viettel.auth.service;

import com.viettel.auth.dto.AuthRequest;
import com.viettel.auth.dto.AuthResponse;
import com.viettel.auth.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private JwtUtil jwtUtil;

    public AuthResponse login(AuthRequest request) {
        // In a real application, you would validate credentials against a database.
        // For demonstration purposes, we'll accept any username/password.
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new RuntimeException("Invalid credentials");
        }
        
        // Ensure dummy validation passes if we had one.
        String accessToken = jwtUtil.generateAccessToken(request.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(request.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (jwtUtil.validateToken(refreshToken)) {
            String username = jwtUtil.extractUsername(refreshToken);
            String newAccessToken = jwtUtil.generateAccessToken(username);
            String newRefreshToken = jwtUtil.generateRefreshToken(username);

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
}
