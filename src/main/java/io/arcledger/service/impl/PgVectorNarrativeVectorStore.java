package io.arcledger.service.impl;

import io.arcledger.domain.SyntheticQuestion;
import io.arcledger.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@ConditionalOnProperty(name = "arcledger.retrieval.vector-store-provider", havingValue = "pgvector", matchIfMissing = true)
public class PgVectorNarrativeVectorStore implements NarrativeVectorStore {
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final int dimensions;
    private final int candidateLimit;
    private final double semanticWeight, entityWeight, versionWeight, recencyWeight;

    public PgVectorNarrativeVectorStore(JdbcTemplate jdbcTemplate, EmbeddingService embeddingService,
        @Value("${arcledger.retrieval.embedding-dimensions:768}") int dimensions,
        @Value("${arcledger.retrieval.candidate-limit:50}") int candidateLimit,
        @Value("${arcledger.retrieval.semantic-weight:0.65}") double semanticWeight,
        @Value("${arcledger.retrieval.entity-weight:0.15}") double entityWeight,
        @Value("${arcledger.retrieval.version-weight:0.10}") double versionWeight,
        @Value("${arcledger.retrieval.recency-weight:0.10}") double recencyWeight) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.dimensions = dimensions;
        this.candidateLimit = candidateLimit;
        this.semanticWeight = semanticWeight;
        this.entityWeight = entityWeight;
        this.versionWeight = versionWeight;
        this.recencyWeight = recencyWeight;
    }

    @Override
    public void index(SyntheticQuestion question, double[] embedding) {
        String vector = vectorLiteral(embedding);
        int updated = jdbcTemplate.update(
            "UPDATE synthetic_questions SET embedding = CAST(? AS vector) WHERE id = ?", vector, question.getId());
        if (updated != 1) throw new IllegalStateException("Synthetic question was not available for vector indexing: " + question.getId());
    }

    @Override
    public List<RetrievalHit> search(UUID storyId, String query, UUID entityId, int limit) {
        String vector = vectorLiteral(embeddingService.embed(query));
        String entityFilter = entityId == null ? "" : " AND q.entity_id = ?";
        String sql = """
            SELECT q.id, q.entity_id, q.state_version_id, q.scene_id, e.name AS entity_name,
                   q.question, q.answer, q.current_state, v.version_number, q.created_at,
                   1 - (q.embedding <=> CAST(? AS vector)) AS semantic_similarity
              FROM synthetic_questions q
              JOIN narrative_entities e ON e.id = q.entity_id
              JOIN entity_state_versions v ON v.id = q.state_version_id
             WHERE q.story_id = ? AND q.current_state = TRUE AND q.embedding IS NOT NULL
            """ + entityFilter + " ORDER BY q.embedding <=> CAST(? AS vector) LIMIT ?";
        int candidates = Math.max(Math.max(1, limit), candidateLimit);
        Object[] parameters = entityId == null
            ? new Object[] {vector, storyId, vector, candidates}
            : new Object[] {vector, storyId, entityId, vector, candidates};
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        return jdbcTemplate.query(sql, this::mapCandidate, parameters).stream()
            .map(candidate -> score(candidate, normalizedQuery))
            .sorted(Comparator.comparingDouble(RetrievalHit::score).reversed())
            .limit(Math.max(1, limit)).toList();
    }

    private Candidate mapCandidate(ResultSet rs, int rowNumber) throws SQLException {
        return new Candidate(rs.getObject("id", UUID.class), rs.getObject("entity_id", UUID.class),
            rs.getObject("state_version_id", UUID.class), rs.getObject("scene_id", UUID.class),
            rs.getString("entity_name"), rs.getString("question"), rs.getString("answer"),
            rs.getBoolean("current_state"), rs.getInt("version_number"),
            rs.getTimestamp("created_at").toInstant(), rs.getDouble("semantic_similarity"));
    }

    private RetrievalHit score(Candidate candidate, String normalizedQuery) {
        double entity = normalizedQuery.contains(candidate.entityName().toLowerCase(Locale.ROOT)) ? 1.0 : 0.0;
        double validity = candidate.current() ? 1.0 : 0.0;
        long ageDays = Math.max(0, ChronoUnit.DAYS.between(candidate.createdAt(), Instant.now()));
        double recency = 1.0 / (1.0 + ageDays / 30.0);
        double score = semanticWeight * candidate.semanticSimilarity() + entityWeight * entity
            + versionWeight * validity + recencyWeight * recency;
        return new RetrievalHit(candidate.id(), candidate.entityId(), candidate.versionId(), candidate.sceneId(),
            candidate.entityName(), candidate.question(), candidate.answer(), candidate.current(), candidate.version(), score);
    }

    private String vectorLiteral(double[] embedding) {
        if (embedding.length != dimensions) {
            throw new IllegalArgumentException("Expected " + dimensions + " embedding dimensions but received " + embedding.length);
        }
        StringJoiner values = new StringJoiner(",", "[", "]");
        for (double value : embedding) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Embedding contains a non-finite value");
            values.add(Double.toString(value));
        }
        return values.toString();
    }

    private record Candidate(UUID id, UUID entityId, UUID versionId, UUID sceneId, String entityName,
                             String question, String answer, boolean current, int version,
                             Instant createdAt, double semanticSimilarity) {}
}
