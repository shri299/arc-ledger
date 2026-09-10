package io.arcledger.domain;

public enum ProcessingJobStatus {
    QUEUED,
    PROCESSING,
    RETRYING,
    COMPLETED,
    DEAD_LETTER
}
