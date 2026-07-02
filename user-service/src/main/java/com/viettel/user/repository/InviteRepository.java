package com.viettel.user.repository;

import com.viettel.user.entity.Invite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.tokenHash = :hash")
    Optional<Invite> findByTokenHashForUpdate(@Param("hash") String hash);
    boolean existsByEmailIgnoreCaseAndRoleIdAndStatus(String email, Integer roleId, Invite.Status status);
}
