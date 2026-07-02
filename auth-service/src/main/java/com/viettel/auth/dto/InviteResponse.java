package com.viettel.auth.dto;
import java.time.Instant;
public record InviteResponse(long id, String email, String role, Instant expiresAt, String status) {}
