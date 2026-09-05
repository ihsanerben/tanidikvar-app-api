package com.tanidikvar.api.auth.repository;

import com.tanidikvar.api.auth.entity.AuthSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    boolean existsByUserIdAndFamilyIdAndReplacedByIdIsNullAndRevokedAtIsNullAndDeletedAtIsNullAndExpiresAtAfter(
            UUID userId, UUID familyId, Instant now);
    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now, s.updatedAt = :now, s.version = s.version + 1 where s.userId = :userId and s.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);
    @Modifying
    @Query("update AuthSession s set s.revokedAt = :now, s.updatedAt = :now, s.version = s.version + 1 where s.userId = :userId and s.familyId = :familyId and s.revokedAt is null")
    int revokeFamily(@Param("userId") UUID userId, @Param("familyId") UUID familyId, @Param("now") Instant now);
}
