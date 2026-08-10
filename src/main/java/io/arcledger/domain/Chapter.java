package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "chapters", uniqueConstraints = @UniqueConstraint(columnNames = {"story_id", "chapter_number"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chapter {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "story_id") private Story story;
    @Column(name = "chapter_number", nullable = false) private int number;
    @Column(nullable = false) private String title;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public Chapter(Story story, int number, String title) {
        this.id = UUID.randomUUID(); this.story = story; this.number = number; this.title = title;
        this.createdAt = this.updatedAt = Instant.now();
    }
}
