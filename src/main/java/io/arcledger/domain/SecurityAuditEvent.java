package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecurityAuditEvent {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private AppUser actor;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @Column(nullable = false, length = 24)
    private String outcome;
    @Column(name = "target_type", length = 32)
    private String targetType;
    @Column(name = "target_id") private UUID targetId;
    @Column(name = "request_id", length = 64)
    private String requestId;
    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SecurityAuditEvent(AppUser actor, String eventType, String outcome, String targetType,
                              UUID targetId, String requestId, String ipHash) {
        this.id = UUID.randomUUID();
        this.actor = actor;
        this.eventType = eventType;
        this.outcome = outcome;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestId = requestId;
        this.ipHash = ipHash;
        this.createdAt = Instant.now();
    }
}
