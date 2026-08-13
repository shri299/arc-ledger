package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "scenes", uniqueConstraints = @UniqueConstraint(columnNames = {"chapter_id", "sequence_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scene {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "story_id") private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "chapter_id") private Chapter chapter;
    @Column(name = "sequence_number", nullable = false) private int sequence;
    @Column(nullable = false, columnDefinition = "text") private String rawText;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProcessingStatus processingStatus;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public Scene(Story story, Chapter chapter, int sequence, String rawText) {
        this.id = UUID.randomUUID(); this.story = story; this.chapter = chapter; this.sequence = sequence;
        this.rawText = rawText; this.processingStatus = ProcessingStatus.PENDING;
        this.createdAt = this.updatedAt = Instant.now();
    }
    public void processed() { this.processingStatus = ProcessingStatus.PROCESSED; this.updatedAt = Instant.now(); }
    public void failed() { this.processingStatus = ProcessingStatus.FAILED; this.updatedAt = Instant.now(); }
}
