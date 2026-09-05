package com.tanidikvar.api.auth.repository;

import com.tanidikvar.api.auth.entity.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ActionTokenRepository extends JpaRepository<ActionToken, UUID> {
    Optional<ActionToken> findByTokenHash(String hash);
    @Query("select t.userId from ActionToken t where t.tokenHash = :hash")
    Optional<UUID> findUserId(@Param("hash") String hash);
    @Modifying
    @Query("update ActionToken t set t.consumedAt = :now, t.updatedAt = :now, t.version = t.version + 1 where t.userId = :userId and t.purpose = :purpose and t.consumedAt is null")
    int consumeAll(@Param("userId") UUID userId, @Param("purpose") ActionPurpose purpose, @Param("now") Instant now);
}
