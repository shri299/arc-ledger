package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scenes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_scene_chapter_sequence", columnNames = {"chapter_id", "sequence_number"}),
    @UniqueConstraint(name = "uk_scene_story_idempotency", columnNames = {"story_id", "idempotency_key"})
})
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scene {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "story_id") private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "chapter_id") private Chapter chapter;
    @Column(name = "sequence_number", nullable = false) private int sequence;
    @Column(nullable = false, columnDefinition = "text") private String rawText;
    @Column(name = "idempotency_key", length = 128) private String idempotencyKey;
    @Column(name = "request_hash", length = 64) private String requestHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProcessingStatus processingStatus;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public Scene(Story story, Chapter chapter, int sequence, String rawText) {
        this(story, chapter, sequence, rawText, null, null);
    }

    public Scene(Story story, Chapter chapter, int sequence, String rawText, String idempotencyKey, String requestHash) {
        this.id = UUID.randomUUID(); this.story = story; this.chapter = chapter; this.sequence = sequence;
        this.rawText = rawText; this.idempotencyKey = idempotencyKey; this.requestHash = requestHash;
        this.processingStatus = ProcessingStatus.QUEUED;
        this.createdAt = this.updatedAt = Instant.now();
    }
    public void processed() { this.processingStatus = ProcessingStatus.PROCESSED; this.updatedAt = Instant.now(); }
    public void queued() { this.processingStatus = ProcessingStatus.QUEUED; this.updatedAt = Instant.now(); }
    public void processing() { this.processingStatus = ProcessingStatus.PROCESSING; this.updatedAt = Instant.now(); }
    public void retrying() { this.processingStatus = ProcessingStatus.RETRYING; this.updatedAt = Instant.now(); }
    public void deadLetter() { this.processingStatus = ProcessingStatus.DEAD_LETTER; this.updatedAt = Instant.now(); }
    public void failed() { this.processingStatus = ProcessingStatus.FAILED; this.updatedAt = Instant.now(); }
}
