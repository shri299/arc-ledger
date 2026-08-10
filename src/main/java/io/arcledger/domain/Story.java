package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "stories")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Story {
    @Id private UUID id;
    @Column(nullable = false) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public Story(String title, String description) {
        this.id = UUID.randomUUID(); this.title = title; this.description = description;
        this.createdAt = this.updatedAt = Instant.now();
    }
}
