package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_recovery_codes", uniqueConstraints =
    @UniqueConstraint(name = "uk_account_recovery_code_hash", columnNames = "code_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRecoveryCode {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AccountRecoveryCode(AppUser user, String codeHash) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.codeHash = codeHash;
        this.createdAt = Instant.now();
    }

    public boolean isUsable() { return consumedAt == null; }
    public void consume(Instant now) { this.consumedAt = now; }
}
