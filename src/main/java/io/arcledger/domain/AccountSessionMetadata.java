package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "account_session_metadata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountSessionMetadata {
    @Id @Column(name = "session_hash", length = 64)
    private String sessionHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;
    @Column(name = "device_label", nullable = false, length = 160)
    private String deviceLabel;
    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public AccountSessionMetadata(String sessionHash, AppUser user, String deviceLabel, String ipHash) {
        this.sessionHash = sessionHash;
        this.user = user;
        this.deviceLabel = deviceLabel;
        this.ipHash = ipHash;
        this.createdAt = this.lastSeenAt = Instant.now();
    }

    public void seen(Instant now) { this.lastSeenAt = now; }
}
