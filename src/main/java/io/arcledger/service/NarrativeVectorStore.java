package io.arcledger.service;

import java.util.List;
import java.util.UUID;

public interface NarrativeVectorStore {
    List<RetrievalHit> search(UUID storyId, String query, UUID entityId, int limit);
}
