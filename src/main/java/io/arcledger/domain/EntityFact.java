package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "entity_facts", indexes = @Index(name = "idx_fact_current", columnList = "entity_id,active"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EntityFact {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entity_id") private NarrativeEntity entity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "state_version_id") private EntityStateVersion stateVersion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_scene_id") private Scene sourceScene;
    @Column(name = "fact_key", nullable = false) private String key;
    @Column(name = "fact_value", nullable = false, length = 2000) private String value;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private KnowledgeKind knowledgeKind;
    @Column(nullable = false) private boolean active;
    private UUID supersededByFactId;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    public EntityFact(NarrativeEntity entity, EntityStateVersion stateVersion, Scene sourceScene, String key, String value, KnowledgeKind kind) {
        this.id = UUID.randomUUID(); this.entity = entity; this.stateVersion = stateVersion; this.sourceScene = sourceScene;
        this.key = key; this.value = value; this.knowledgeKind = kind; this.active = true; this.createdAt = Instant.now();
    }
    public void supersedeWith(UUID replacementId) { this.active = false; this.supersededByFactId = replacementId; }
}
