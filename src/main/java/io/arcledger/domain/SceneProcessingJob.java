package io.arcledger.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scene_processing_jobs", uniqueConstraints =
    @UniqueConstraint(name = "uk_scene_processing_job_scene", columnNames = "scene_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SceneProcessingJob {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingJobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SceneProcessingJob(Scene scene, int maxAttempts) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.scene = scene;
        this.status = ProcessingJobStatus.QUEUED;
        this.attempts = 0;
        this.maxAttempts = maxAttempts;
        this.availableAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void claim(String workerId, Instant now) {
        if (status != ProcessingJobStatus.QUEUED && status != ProcessingJobStatus.RETRYING) {
            throw new IllegalStateException("Only queued work can be claimed.");
        }
        status = ProcessingJobStatus.PROCESSING;
        attempts++;
        lockedAt = now;
        lockedBy = workerId;
        updatedAt = now;
        scene.processing();
    }

    public void complete(Instant now) {
        status = ProcessingJobStatus.COMPLETED;
        lockedAt = null;
        lockedBy = null;
        lastErrorCode = null;
        updatedAt = now;
    }

    public void recordFailure(String errorCode, Duration retryDelay, Instant now) {
        lastErrorCode = errorCode;
        lockedAt = null;
        lockedBy = null;
        updatedAt = now;
        if (attempts >= maxAttempts) {
            status = ProcessingJobStatus.DEAD_LETTER;
            availableAt = now;
            scene.deadLetter();
        } else {
            status = ProcessingJobStatus.RETRYING;
            availableAt = now.plus(retryDelay);
            scene.retrying();
        }
    }

    public void requeue(Instant now) {
        status = ProcessingJobStatus.QUEUED;
        attempts = 0;
        availableAt = now;
        lockedAt = null;
        lockedBy = null;
        lastErrorCode = null;
        updatedAt = now;
        scene.queued();
    }
}
