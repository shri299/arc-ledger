package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.EntityFactRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EntityStateService {
    private final EntityFactRepository factRepository;
    public EntityStateService(EntityFactRepository factRepository) { this.factRepository = factRepository; }

    public EntityState latest(NarrativeEntity entity) {
        Map<String, EntityState.StateFact> facts = new LinkedHashMap<>();
        factRepository.findByEntityIdAndActiveTrueOrderByKeyAsc(entity.getId()).forEach(fact ->
            facts.put(fact.getKey(), new EntityState.StateFact(fact.getValue(), fact.getKnowledgeKind(), fact.getSourceScene().getId())));
        return new EntityState(entity.getId(), entity.getLatestVersion(), Collections.unmodifiableMap(facts));
    }
}
