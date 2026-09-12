package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "story_invitations", uniqueConstraints =
    @UniqueConstraint(name = "uk_story_invitation_token_hash", columnNames = "token_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryInvitation {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;
    @Column(name = "invited_email", nullable = false, length = 320)
    private String invitedEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private StoryRole role;
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invited_by")
    private AppUser invitedBy;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StoryInvitation(Story story, String invitedEmail, StoryRole role, String tokenHash,
                           AppUser invitedBy, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.story = story;
        this.invitedEmail = invitedEmail;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isUsableAt(Instant now) { return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now); }
    public void accept(Instant now) { this.acceptedAt = now; }
    public void revoke(Instant now) { this.revokedAt = now; }
}
