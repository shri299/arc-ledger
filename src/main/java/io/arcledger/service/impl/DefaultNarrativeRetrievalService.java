package io.arcledger.service.impl;

import io.arcledger.service.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DefaultNarrativeRetrievalService implements NarrativeRetrievalService {
    private final NarrativeVectorStore vectorStore;
    public DefaultNarrativeRetrievalService(NarrativeVectorStore vectorStore) { this.vectorStore = vectorStore; }
    @Override public List<RetrievalHit> retrieve(UUID storyId, String query, UUID entityId, int limit) {
        return vectorStore.search(storyId, query, entityId, limit).stream().filter(RetrievalHit::current).toList();
    }
}
