package io.arcledger.domain;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class SceneProcessingJobTest {
    @Test
    void retriesWithSafeStateThenMovesToDeadLetterAndCanBeRequeued() {
        Story story = new Story("Durable work", "Job transition test");
        Chapter chapter = new Chapter(story, 1, "Opening");
        Scene scene = new Scene(story, chapter, 1, "Mira enters the observatory.");
        SceneProcessingJob job = new SceneProcessingJob(scene, 2);
        Instant now = Instant.parse("2026-09-10T12:00:00Z");

        job.claim("worker-a", now);
        job.recordFailure("MODEL_TIMEOUT", Duration.ofSeconds(15), now.plusSeconds(1));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.RETRYING);
        assertThat(scene.getProcessingStatus()).isEqualTo(ProcessingStatus.RETRYING);
        assertThat(job.getLastErrorCode()).isEqualTo("MODEL_TIMEOUT");
        assertThat(job.getAvailableAt()).isEqualTo(now.plusSeconds(16));

        job.claim("worker-b", now.plusSeconds(16));
        job.recordFailure("PROCESSING_FAILURE", Duration.ofSeconds(30), now.plusSeconds(17));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.DEAD_LETTER);
        assertThat(scene.getProcessingStatus()).isEqualTo(ProcessingStatus.DEAD_LETTER);
        assertThat(job.getAttempts()).isEqualTo(2);

        job.requeue(now.plusSeconds(30));

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.QUEUED);
        assertThat(scene.getProcessingStatus()).isEqualTo(ProcessingStatus.QUEUED);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getLastErrorCode()).isNull();
    }
}
