package io.arcledger.service.impl;

import io.arcledger.domain.EntityState;
import io.arcledger.domain.Scene;
import io.arcledger.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "arcledger.inference.enabled", havingValue = "false")
public class NoOpNarrativeInferenceService implements NarrativeInferenceService {
    @Override public List<QuestionCandidate> expandQuestions(String entityName, EntityState state) { return List.of(); }
    @Override public List<ConsistencyAdvisory> analyzeConsistency(Scene scene, String entityName,
        EntityState state, ExtractedEntity extracted) { return List.of(); }
    @Override public Optional<String> composeGroundedAnswer(String query, List<RetrievalHit> evidence) { return Optional.empty(); }
}
