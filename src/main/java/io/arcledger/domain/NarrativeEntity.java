package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "narrative_entities", uniqueConstraints = @UniqueConstraint(columnNames = {"story_id", "normalized_name"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NarrativeEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "story_id") private Story story;
    @Column(nullable = false) private String name;
    @Column(name = "normalized_name", nullable = false) private String normalizedName;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EntityType type;
    @Column(nullable = false) private int latestVersion;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public NarrativeEntity(Story story, String name, EntityType type) {
        this.id = UUID.randomUUID(); this.story = story; this.name = name;
        this.normalizedName = name.strip().toLowerCase(); this.type = type; this.latestVersion = 0;
        this.createdAt = this.updatedAt = Instant.now();
    }
    public int nextVersion() { this.updatedAt = Instant.now(); return ++latestVersion; }
}
