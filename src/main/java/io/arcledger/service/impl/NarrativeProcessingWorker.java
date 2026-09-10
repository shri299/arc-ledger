package io.arcledger.service.impl;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.http.HttpTimeoutException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "arcledger.processing.background-enabled", havingValue = "true", matchIfMissing = true)
public class NarrativeProcessingWorker {
    private static final Logger log = LoggerFactory.getLogger(NarrativeProcessingWorker.class);

    private final SceneProcessingCoordinator coordinator;
    private final int batchSize;
    private final String workerId;

    public NarrativeProcessingWorker(SceneProcessingCoordinator coordinator,
                                     @Value("${arcledger.processing.batch-size:1}") int batchSize) {
        this.coordinator = coordinator;
        this.batchSize = Math.max(1, batchSize);
        String hostname = System.getenv("HOSTNAME");
        this.workerId = (hostname == null || hostname.isBlank()) ? UUID.randomUUID().toString() : hostname;
    }

    @Scheduled(fixedDelayString = "${arcledger.processing.poll-delay-ms:1000}",
               initialDelayString = "${arcledger.processing.poll-delay-ms:1000}",
               timeUnit = TimeUnit.MILLISECONDS)
    public void processAvailableWork() {
        for (int index = 0; index < batchSize; index++) {
            var jobId = coordinator.claimNext(workerId);
            if (jobId.isEmpty()) return;
            try {
                coordinator.processClaimed(jobId.get());
            } catch (RuntimeException exception) {
                String errorCode = classify(exception);
                log.warn("Scene processing failed job_id={} error_code={} exception_type={}",
                    jobId.get(), errorCode, exception.getClass().getName());
                coordinator.recordFailure(jobId.get(), errorCode);
            }
        }
    }

    @Scheduled(fixedDelay = 60, initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void recoverExpiredWork() {
        int recovered = coordinator.recoverExpiredLeases();
        if (recovered > 0) log.warn("Recovered expired scene-processing leases count={}", recovered);
    }

    @Scheduled(cron = "0 25 3 * * *")
    public void pruneCompletedWork() {
        long deleted = coordinator.pruneCompletedJobs();
        if (deleted > 0) log.info("Pruned completed scene-processing jobs count={}", deleted);
    }

    private String classify(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException || current instanceof java.net.SocketTimeoutException) {
                return "MODEL_TIMEOUT";
            }
            current = current.getCause();
        }
        return "PROCESSING_FAILURE";
    }
}
