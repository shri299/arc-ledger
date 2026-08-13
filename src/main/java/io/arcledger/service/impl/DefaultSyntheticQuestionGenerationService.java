package io.arcledger.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.*;
import io.arcledger.repository.SyntheticQuestionRepository;
import io.arcledger.service.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DefaultSyntheticQuestionGenerationService implements SyntheticQuestionGenerationService {
    private final SyntheticQuestionRepository repository;
    private final EmbeddingService embeddings;
    private final NarrativeInferenceService inferenceService;
    private final ObjectMapper objectMapper;

    public DefaultSyntheticQuestionGenerationService(SyntheticQuestionRepository repository, EmbeddingService embeddings,
        NarrativeInferenceService inferenceService, ObjectMapper objectMapper) {
        this.repository = repository; this.embeddings = embeddings; this.inferenceService = inferenceService;
        this.objectMapper = objectMapper;
    }
    @Override
    public void generateAndIndex(EntityStateVersion version, EntityState state) {
        repository.findByEntityIdAndCurrentTrue(version.getEntity().getId()).forEach(SyntheticQuestion::markObsolete);
        String name = version.getEntity().getName();
        Map<String, String> candidates = new LinkedHashMap<>();
        state.facts().forEach((key, fact) -> questions(name, key, fact.value())
            .forEach(question -> candidates.putIfAbsent(question, key)));
        inferenceService.expandQuestions(name, state).forEach(candidate -> {
            if (candidate.question() != null && !candidate.question().isBlank()
                && candidate.factKey() != null && state.facts().containsKey(candidate.factKey())) {
                candidates.putIfAbsent(candidate.question().strip(), candidate.factKey());
            }
        });

        List<Map.Entry<String, String>> entries = new ArrayList<>(candidates.entrySet());
        List<double[]> vectors = embeddings.embedAll(entries.stream().map(Map.Entry::getKey).toList());
        for (int index = 0; index < entries.size(); index++) {
            Map.Entry<String, String> candidate = entries.get(index);
            EntityState.StateFact fact = state.facts().get(candidate.getValue());
            String answer = name + " — " + humanize(candidate.getValue()) + ": " + fact.value() + ".";
            try {
                repository.save(new SyntheticQuestion(version, candidate.getKey(), answer,
                    objectMapper.writeValueAsString(vectors.get(index))));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize embedding", exception);
            }
        }
    }
    private List<String> questions(String name, String key, String value) {
        List<String> result = new ArrayList<>();
        result.add("What is " + name + "'s " + humanize(key) + "?");
        result.add("What is currently known about " + humanize(key) + " for " + name + "?");
        if (key.toLowerCase().contains("armstatus")) result.add("Does " + name + " still have both arms?");
        if (key.equalsIgnoreCase("location")) result.add("Where is " + name + " currently located?");
        if (key.equalsIgnoreCase("lifeStatus")) result.add("Is " + name + " alive?");
        return result;
    }
    private String humanize(String key) { return key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(); }
}
