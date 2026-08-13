package io.arcledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.EntityState;
import io.arcledger.domain.KnowledgeKind;
import io.arcledger.service.impl.ModelNarrativeInferenceService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModelNarrativeInferenceServiceTest {
    private final PromptTemplateService prompts = new PromptTemplateService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsOnlyModelQuestionCandidatesForExistingFactsAtTheConsumerBoundary() {
        LanguageModelClient client = (prompt, input) ->
            "{\"questions\":[{\"question\":\"Where was John last seen?\",\"factKey\":\"location\"}]}";
        ModelNarrativeInferenceService service = new ModelNarrativeInferenceService(client, prompts, objectMapper);
        EntityState state = new EntityState(UUID.randomUUID(), 1, Map.of("location",
            new EntityState.StateFact("Paris", KnowledgeKind.FACT, UUID.randomUUID())));

        List<NarrativeInferenceService.QuestionCandidate> candidates = service.expandQuestions("John", state);

        assertThat(candidates).containsExactly(
            new NarrativeInferenceService.QuestionCandidate("Where was John last seen?", "location"));
    }

    @Test
    void returnsEmptyWhenModelInferenceFailsSoDeterministicFallbacksRemainAvailable() {
        LanguageModelClient client = (prompt, input) -> { throw new IllegalStateException("offline"); };
        ModelNarrativeInferenceService service = new ModelNarrativeInferenceService(client, prompts, objectMapper);

        assertThat(service.composeGroundedAnswer("Where is John?", List.of())).isEmpty();
        assertThat(service.expandQuestions("John", new EntityState(UUID.randomUUID(), 0, Map.of()))).isEmpty();
    }
}
