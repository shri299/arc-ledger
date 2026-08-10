package io.arcledger.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.SyntheticQuestion;
import io.arcledger.repository.SyntheticQuestionRepository;
import io.arcledger.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class JpaNarrativeVectorStore implements NarrativeVectorStore {
    private final SyntheticQuestionRepository repository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final double semanticWeight, entityWeight, versionWeight, recencyWeight;

    public JpaNarrativeVectorStore(SyntheticQuestionRepository repository, EmbeddingService embeddingService, ObjectMapper objectMapper,
        @Value("${arcledger.retrieval.semantic-weight:0.65}") double semanticWeight,
        @Value("${arcledger.retrieval.entity-weight:0.15}") double entityWeight,
        @Value("${arcledger.retrieval.version-weight:0.10}") double versionWeight,
        @Value("${arcledger.retrieval.recency-weight:0.10}") double recencyWeight) {
        this.repository = repository; this.embeddingService = embeddingService; this.objectMapper = objectMapper;
        this.semanticWeight = semanticWeight; this.entityWeight = entityWeight;
        this.versionWeight = versionWeight; this.recencyWeight = recencyWeight;
    }
    @Override
    public List<RetrievalHit> search(UUID storyId, String query, UUID entityId, int limit) {
        double[] queryVector = embeddingService.embed(query);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return repository.findByStoryId(storyId).stream()
            .filter(question -> entityId == null || question.getEntity().getId().equals(entityId))
            .map(question -> hit(question, queryVector, normalizedQuery))
            .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
            .limit(Math.max(1, limit)).toList();
    }
    private RetrievalHit hit(SyntheticQuestion q, double[] queryVector, String normalizedQuery) {
        double semantic = cosine(queryVector, deserialize(q.getEmbeddingJson()));
        double entity = normalizedQuery.contains(q.getEntity().getName().toLowerCase(Locale.ROOT)) ? 1.0 : 0.0;
        double validity = q.isCurrent() ? 1.0 : 0.0;
        long ageDays = Math.max(0, ChronoUnit.DAYS.between(q.getCreatedAt(), Instant.now()));
        double recency = 1.0 / (1.0 + ageDays / 30.0);
        double score = semanticWeight * semantic + entityWeight * entity + versionWeight * validity + recencyWeight * recency;
        if (!q.isCurrent()) score -= 0.20;
        return new RetrievalHit(q.getId(), q.getEntity().getId(), q.getStateVersion().getId(), q.getScene().getId(),
            q.getEntity().getName(), q.getQuestion(), q.getAnswer(), q.isCurrent(), q.getStateVersion().getVersion(), score);
    }
    private double[] deserialize(String json) {
        try { return objectMapper.readValue(json, double[].class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid stored embedding", exception); }
    }
    private double cosine(double[] left, double[] right) {
        double dot = 0, a = 0, b = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) { dot += left[i] * right[i]; a += left[i] * left[i]; b += right[i] * right[i]; }
        return a == 0 || b == 0 ? 0 : dot / (Math.sqrt(a) * Math.sqrt(b));
    }
}
