package com.tanidikvar.api.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class Account {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Authority authority;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant deletedAt;
    private Instant emailVerifiedAt;
    @Version private long version;

    protected Account() { }
    public Account(String email, String passwordHash, Instant now) {
        this.id = UUID.randomUUID(); this.email = email; this.passwordHash = passwordHash;
        this.authority = Authority.MEMBER; this.createdAt = now; this.updatedAt = now;
    }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public void verifyEmail(Instant now) { emailVerifiedAt = now; updatedAt = now; }
    public void changePassword(String hash, Instant now) { passwordHash = hash; updatedAt = now; }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Authority getAuthority() { return authority; }
    public boolean isDeleted() { return deletedAt != null; }
}
