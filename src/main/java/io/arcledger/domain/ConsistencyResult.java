package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "consistency_results")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsistencyResult {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "story_id") private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "scene_id") private Scene scene;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "entity_id") private NarrativeEntity entity;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ValidationStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Severity severity;
    @Column(nullable = false, length = 2000) private String description;
    @Column(length = 4000) private String supportingEvidence;
    @Column(length = 1000) private String sourceSceneIds;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    public ConsistencyResult(Story story, Scene scene, NarrativeEntity entity, ValidationStatus status,
                             Severity severity, String description, String evidence, String sourceSceneIds) {
        this.id = UUID.randomUUID(); this.story = story; this.scene = scene; this.entity = entity;
        this.status = status; this.severity = severity; this.description = description;
        this.supportingEvidence = evidence; this.sourceSceneIds = sourceSceneIds; this.createdAt = Instant.now();
    }
}
