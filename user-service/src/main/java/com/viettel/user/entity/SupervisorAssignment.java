package com.viettel.user.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "supervisor_assignments")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SupervisorAssignment {
    @Id private Long userId;
    @ElementCollection
    @CollectionTable(name = "supervisor_teams", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "team_code", length = 100)
    @Builder.Default private Set<String> teams = new HashSet<>();
    @ElementCollection
    @CollectionTable(name = "supervisor_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission_code", length = 100)
    @Builder.Default private Set<String> permissions = new HashSet<>();
}
