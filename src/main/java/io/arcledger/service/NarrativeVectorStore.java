package io.arcledger.service;

import java.util.List;
import java.util.UUID;
import io.arcledger.domain.SyntheticQuestion;

public interface NarrativeVectorStore {
    void index(SyntheticQuestion question, double[] embedding);
    List<RetrievalHit> search(UUID storyId, String query, UUID entityId, int limit);
}
