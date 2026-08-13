package io.arcledger.service;

import io.arcledger.domain.EntityState;
import io.arcledger.domain.Scene;

import java.util.List;
import java.util.Optional;

public interface NarrativeInferenceService {
    List<QuestionCandidate> expandQuestions(String entityName, EntityState state);

    List<ConsistencyAdvisory> analyzeConsistency(Scene scene, String entityName, EntityState state,
                                                 ExtractedEntity extracted);

    Optional<String> composeGroundedAnswer(String query, List<RetrievalHit> evidence);

    record QuestionCandidate(String question, String factKey) {}

    record ConsistencyAdvisory(String description, String evidence, String factKey,
                               boolean insufficientContext) {}
}
