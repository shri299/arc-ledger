package io.arcledger.service;

import java.util.List;
import java.util.UUID;

public interface NarrativeRetrievalService {
    List<RetrievalHit> retrieve(UUID storyId, String query, UUID entityId, int limit);
}
