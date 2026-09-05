package com.tanidikvar.api.auth.repository;

import com.tanidikvar.api.auth.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Query("select u.id from Account u where u.email = :email")
    Optional<UUID> findIdByEmail(@Param("email") String email);
    @Modifying
    @Query(value = "INSERT INTO users(id,email,password_hash,authority,created_at,updated_at) VALUES (:id,:email,:hash,'MEMBER',:now,:now) ON CONFLICT(email) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("email") String email, @Param("hash") String hash, @Param("now") java.time.Instant now);
    Optional<Account> findByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Account u where u.id = :id")
    Optional<Account> lockById(@Param("id") UUID id);
}
