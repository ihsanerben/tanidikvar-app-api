package com.tanidikvar.api.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_action_tokens")
public class ActionToken {
    @Id private UUID id;
    @Column(nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ActionPurpose purpose;
    @Column(nullable = false, length = 64, unique = true) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant deletedAt;
    @Version private long version;
    protected ActionToken() { }
    public ActionToken(UUID userId, ActionPurpose purpose, String hash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID(); this.userId = userId; this.purpose = purpose; this.tokenHash = hash;
        this.expiresAt = expiresAt; this.createdAt = now; this.updatedAt = now;
    }
    public UUID getUserId() { return userId; }
    public boolean usable(ActionPurpose expected, Instant now) {
        return purpose == expected && consumedAt == null && deletedAt == null && expiresAt.isAfter(now);
    }
}
