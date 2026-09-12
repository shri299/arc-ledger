package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(name = "uk_app_user_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {
    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 16)
    private AccountRole accountRole;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public AppUser(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.enabled = true;
        this.accountRole = AccountRole.USER;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public void verifyEmail(Instant now) { this.emailVerifiedAt = now; this.updatedAt = now; }
    public void changePassword(String passwordHash, Instant now) { this.passwordHash = passwordHash; this.updatedAt = now; }
    public void suspend(Instant now) { this.suspendedAt = now; this.enabled = false; this.updatedAt = now; }
    public void restore(Instant now) { this.suspendedAt = null; this.enabled = true; this.updatedAt = now; }
    public void changeAccountRole(AccountRole role, Instant now) { this.accountRole = role; this.updatedAt = now; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
}
