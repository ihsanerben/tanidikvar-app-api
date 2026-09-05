package com.tanidikvar.api.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID familyId;
    @Column(nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    private UUID replacedById;
    private Instant revokedAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant deletedAt;
    @Version private long version;

    protected AuthSession() { }
    public AuthSession(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant now) {
        this.id = id; this.userId = userId; this.familyId = familyId; this.tokenHash = tokenHash;
        this.expiresAt = expiresAt; this.createdAt = now; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isActive(Instant now) {
        return replacedById == null && revokedAt == null && deletedAt == null && expiresAt.isAfter(now);
    }
    public void retire(Instant now) { revokedAt = now; updatedAt = now; }
    public void replaceWith(UUID replacement, Instant now) { replacedById = replacement; updatedAt = now; }
}
