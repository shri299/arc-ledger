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
    private final ObjectMapper objectMapper;

    public DefaultSyntheticQuestionGenerationService(SyntheticQuestionRepository repository, EmbeddingService embeddings, ObjectMapper objectMapper) {
        this.repository = repository; this.embeddings = embeddings; this.objectMapper = objectMapper;
    }
    @Override
    public void generateAndIndex(EntityStateVersion version, EntityState state) {
        repository.findByEntityIdAndCurrentTrue(version.getEntity().getId()).forEach(SyntheticQuestion::markObsolete);
        String name = version.getEntity().getName();
        state.facts().forEach((key, fact) -> questions(name, key, fact.value()).forEach(question -> {
            String answer = name + " — " + humanize(key) + ": " + fact.value() + ".";
            try {
                repository.save(new SyntheticQuestion(version, question, answer,
                    objectMapper.writeValueAsString(embeddings.embed(question))));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize embedding", exception);
            }
        }));
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
