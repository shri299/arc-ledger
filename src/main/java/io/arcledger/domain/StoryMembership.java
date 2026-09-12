package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "story_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryMembership {
    @EmbeddedId private StoryMembershipId id;
    @MapsId("storyId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id") private Story story;
    @MapsId("userId") @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id") private AppUser user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private StoryRole role;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "invited_by")
    private AppUser invitedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StoryMembership(Story story, AppUser user, StoryRole role, AppUser invitedBy) {
        this.id = new StoryMembershipId(story.getId(), user.getId());
        this.story = story;
        this.user = user;
        this.role = role;
        this.invitedBy = invitedBy;
        this.createdAt = this.updatedAt = Instant.now();
    }

    public void changeRole(StoryRole role) { this.role = role; this.updatedAt = Instant.now(); }
}
