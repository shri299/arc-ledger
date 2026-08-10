package io.arcledger.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.*;
import io.arcledger.repository.*;
import io.arcledger.service.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EntityStateVersionService {
    private final EntityFactRepository factRepository;
    private final EntityStateVersionRepository versionRepository;
    private final NarrativeEntityRepository entityRepository;
    private final SyntheticQuestionGenerationService questionGenerationService;
    private final ObjectMapper objectMapper;

    public EntityStateVersionService(EntityFactRepository factRepository, EntityStateVersionRepository versionRepository,
        NarrativeEntityRepository entityRepository, SyntheticQuestionGenerationService questionGenerationService, ObjectMapper objectMapper) {
        this.factRepository = factRepository; this.versionRepository = versionRepository; this.entityRepository = entityRepository;
        this.questionGenerationService = questionGenerationService; this.objectMapper = objectMapper;
    }

    public EntityState createVersion(NarrativeEntity entity, Scene scene, Map<String, ExtractedFact> changes) {
        Map<String, EntityFact> existing = new LinkedHashMap<>();
        factRepository.findByEntityIdAndActiveTrueOrderByKeyAsc(entity.getId()).forEach(fact -> existing.put(fact.getKey(), fact));
        Map<String, String> snapshot = new LinkedHashMap<>();
        existing.forEach((key, fact) -> snapshot.put(key, fact.getValue()));
        changes.forEach((key, fact) -> snapshot.put(key, fact.value()));

        int versionNumber = entity.nextVersion();
        entityRepository.save(entity);
        EntityStateVersion version = versionRepository.save(new EntityStateVersion(entity, scene, versionNumber,
            json(changes.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value(), (a, b) -> b, LinkedHashMap::new))),
            json(snapshot)));

        Map<String, EntityState.StateFact> stateFacts = new LinkedHashMap<>();
        existing.forEach((key, oldFact) -> stateFacts.put(key,
            new EntityState.StateFact(oldFact.getValue(), oldFact.getKnowledgeKind(), oldFact.getSourceScene().getId())));
        changes.forEach((key, extracted) -> {
            EntityFact replacement = factRepository.save(new EntityFact(entity, version, scene, key, extracted.value(), extracted.knowledgeKind()));
            EntityFact old = existing.get(key);
            if (old != null) { old.supersedeWith(replacement.getId()); factRepository.save(old); }
            stateFacts.put(key, new EntityState.StateFact(extracted.value(), extracted.knowledgeKind(), scene.getId()));
        });

        EntityState state = new EntityState(entity.getId(), versionNumber, Collections.unmodifiableMap(stateFacts));
        questionGenerationService.generateAndIndex(version, state);
        return state;
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize state version", exception); }
    }
}
