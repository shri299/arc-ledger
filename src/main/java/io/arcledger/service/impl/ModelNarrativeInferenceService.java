package io.arcledger.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.EntityState;
import io.arcledger.domain.Scene;
import io.arcledger.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "arcledger.inference.enabled", havingValue = "true", matchIfMissing = true)
public class ModelNarrativeInferenceService implements NarrativeInferenceService {
    private static final Logger log = LoggerFactory.getLogger(ModelNarrativeInferenceService.class);

    private final LanguageModelClient modelClient;
    private final PromptTemplateService prompts;
    private final ObjectMapper objectMapper;

    public ModelNarrativeInferenceService(LanguageModelClient modelClient, PromptTemplateService prompts,
                                          ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.prompts = prompts;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<QuestionCandidate> expandQuestions(String entityName, EntityState state) {
        if (state.facts().isEmpty()) return List.of();
        try {
            String input = objectMapper.writeValueAsString(Map.of(
                "entityName", entityName,
                "canonicalFacts", state.facts()));
            JsonNode root = json("synthetic-question-generation", input);
            return objectMapper.convertValue(root.path("questions"), new TypeReference<>() {});
        } catch (RuntimeException exception) {
            log.warn("Model question expansion failed; template questions remain available: {}", exception.getMessage());
            return List.of();
        } catch (Exception exception) {
            log.warn("Could not prepare question expansion input", exception);
            return List.of();
        }
    }

    @Override
    public List<ConsistencyAdvisory> analyzeConsistency(Scene scene, String entityName,
                                                         EntityState state, ExtractedEntity extracted) {
        if (state.facts().isEmpty() || extracted.facts().isEmpty()) return List.of();
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("entityName", entityName);
            input.put("canonicalFacts", state.facts());
            input.put("newScene", scene.getRawText());
            input.put("extractedFacts", extracted.facts());
            JsonNode root = json("consistency-validation", objectMapper.writeValueAsString(input));
            return objectMapper.convertValue(root.path("issues"), new TypeReference<>() {});
        } catch (RuntimeException exception) {
            log.warn("Semantic consistency review failed; deterministic validation remains active: {}", exception.getMessage());
            return List.of();
        } catch (Exception exception) {
            log.warn("Could not prepare consistency review input", exception);
            return List.of();
        }
    }

    @Override
    public Optional<String> composeGroundedAnswer(String query, List<RetrievalHit> evidence) {
        if (evidence.isEmpty()) return Optional.empty();
        try {
            String input = objectMapper.writeValueAsString(Map.of("query", query, "canonicalEvidence", evidence));
            JsonNode root = json("grounded-answer-generation", input);
            if (!root.path("supported").asBoolean(false)) return Optional.empty();
            String answer = root.path("answer").asText();
            return answer.isBlank() ? Optional.empty() : Optional.of(answer);
        } catch (RuntimeException exception) {
            log.warn("Grounded answer generation failed; deterministic answer will be returned: {}", exception.getMessage());
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Could not prepare grounded answer input", exception);
            return Optional.empty();
        }
    }

    private JsonNode json(String promptName, String input) throws Exception {
        String response = modelClient.structuredCompletion(prompts.load(promptName), input);
        return objectMapper.readTree(response);
    }
}
