package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "entity_state_versions", uniqueConstraints = @UniqueConstraint(columnNames = {"entity_id", "version_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EntityStateVersion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entity_id") private NarrativeEntity entity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "story_id") private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "originating_scene_id") private Scene originatingScene;
    @Column(name = "version_number", nullable = false) private int version;
    @Column(nullable = false, columnDefinition = "text") private String changedFactsJson;
    @Column(nullable = false, columnDefinition = "text") private String resultingStateJson;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    public EntityStateVersion(NarrativeEntity entity, Scene scene, int version, String changedFactsJson, String resultingStateJson) {
        this.id = UUID.randomUUID(); this.entity = entity; this.story = entity.getStory(); this.originatingScene = scene;
        this.version = version; this.changedFactsJson = changedFactsJson; this.resultingStateJson = resultingStateJson;
        this.createdAt = Instant.now();
    }
}
