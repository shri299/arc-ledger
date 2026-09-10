package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
public class SceneProcessingCoordinator {
    private final SceneProcessingJobRepository jobs;
    private final StoryRepository stories;
    private final NarrativeProcessingPipeline pipeline;
    private final int maxAttempts;
    private final long retryBaseSeconds;
    private final long retryMaxSeconds;
    private final long leaseSeconds;
    private final long completedRetentionDays;

    public SceneProcessingCoordinator(SceneProcessingJobRepository jobs, StoryRepository stories,
        NarrativeProcessingPipeline pipeline,
        @Value("${arcledger.processing.max-attempts:3}") int maxAttempts,
        @Value("${arcledger.processing.retry-base-seconds:15}") long retryBaseSeconds,
        @Value("${arcledger.processing.retry-max-seconds:300}") long retryMaxSeconds,
        @Value("${arcledger.processing.lease-seconds:420}") long leaseSeconds,
        @Value("${arcledger.processing.completed-retention-days:30}") long completedRetentionDays) {
        this.jobs = jobs;
        this.stories = stories;
        this.pipeline = pipeline;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBaseSeconds = Math.max(1, retryBaseSeconds);
        this.retryMaxSeconds = Math.max(this.retryBaseSeconds, retryMaxSeconds);
        this.leaseSeconds = Math.max(30, leaseSeconds);
        this.completedRetentionDays = Math.max(1, completedRetentionDays);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SceneProcessingJob enqueue(Scene scene) {
        return jobs.save(new SceneProcessingJob(scene, maxAttempts));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> claimNext(String workerId) {
        Instant now = Instant.now();
        return jobs.findClaimableId(now).stream().findFirst()
            .map(UUID::fromString)
            .flatMap(jobs::findById)
            .filter(job -> job.getStatus() == ProcessingJobStatus.QUEUED ||
                job.getStatus() == ProcessingJobStatus.RETRYING)
            .map(job -> {
                job.claim(workerId, now);
                return job.getId();
            });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processClaimed(UUID jobId) {
        SceneProcessingJob job = jobs.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() != ProcessingJobStatus.PROCESSING) return;
        stories.findLocked(job.getScene().getStory().getId()).orElseThrow();
        pipeline.process(job.getScene());
        job.complete(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID jobId, String errorCode) {
        jobs.findByIdForUpdate(jobId).ifPresent(job -> {
            if (job.getStatus() != ProcessingJobStatus.PROCESSING) return;
            job.recordFailure(errorCode, retryDelay(job.getAttempts()), Instant.now());
        });
    }

    @Transactional
    public Scene requeue(Scene scene) {
        if (scene.getProcessingStatus() != ProcessingStatus.DEAD_LETTER &&
            scene.getProcessingStatus() != ProcessingStatus.FAILED) {
            return scene;
        }
        SceneProcessingJob job = jobs.findBySceneId(scene.getId())
            .orElseGet(() -> jobs.save(new SceneProcessingJob(scene, maxAttempts)));
        job.requeue(Instant.now());
        return scene;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverExpiredLeases() {
        Instant now = Instant.now();
        List<SceneProcessingJob> stale = jobs.findStale(
            ProcessingJobStatus.PROCESSING, now.minusSeconds(leaseSeconds));
        stale.forEach(job -> job.recordFailure("WORKER_LEASE_EXPIRED", retryDelay(job.getAttempts()), now));
        return stale.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long pruneCompletedJobs() {
        return jobs.deleteByStatusAndUpdatedAtBefore(
            ProcessingJobStatus.COMPLETED, Instant.now().minus(completedRetentionDays, java.time.temporal.ChronoUnit.DAYS));
    }

    private Duration retryDelay(int attempts) {
        int exponent = Math.max(0, Math.min(20, attempts - 1));
        long delay = retryBaseSeconds * (1L << exponent);
        return Duration.ofSeconds(Math.min(retryMaxSeconds, delay));
    }
}
