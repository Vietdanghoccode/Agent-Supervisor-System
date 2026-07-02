package com.viettel.user.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "invites")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Invite {
    public enum Status { PENDING, CLAIMED, CONSUMED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private Integer roleId;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant consumedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status;
    @ElementCollection
    @CollectionTable(name = "invite_teams", joinColumns = @JoinColumn(name = "invite_id"))
    @Column(name = "team_code", length = 100)
    @Builder.Default
    private Set<String> teams = new HashSet<>();
    @ElementCollection
    @CollectionTable(name = "invite_permissions", joinColumns = @JoinColumn(name = "invite_id"))
    @Column(name = "permission_code", length = 100)
    @Builder.Default
    private Set<String> permissions = new HashSet<>();
}
