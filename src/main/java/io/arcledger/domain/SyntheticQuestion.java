package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "synthetic_questions", indexes = @Index(name = "idx_question_story_current", columnList = "story_id,current_state"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyntheticQuestion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "story_id") private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entity_id") private NarrativeEntity entity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "state_version_id") private EntityStateVersion stateVersion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "scene_id") private Scene scene;
    @Column(nullable = false, length = 1000) private String question;
    @Column(nullable = false, length = 2000) private String answer;
    @Lob @Column(nullable = false) private String embeddingJson;
    @Column(name = "current_state", nullable = false) private boolean current;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    public SyntheticQuestion(EntityStateVersion version, String question, String answer, String embeddingJson) {
        this.id = UUID.randomUUID(); this.story = version.getStory(); this.entity = version.getEntity();
        this.stateVersion = version; this.scene = version.getOriginatingScene(); this.question = question;
        this.answer = answer; this.embeddingJson = embeddingJson; this.current = true; this.createdAt = Instant.now();
    }
    public void markObsolete() { this.current = false; }
}
