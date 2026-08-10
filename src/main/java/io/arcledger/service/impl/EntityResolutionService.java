package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.NarrativeEntityRepository;
import org.springframework.stereotype.Service;

@Service
public class EntityResolutionService {
    private final NarrativeEntityRepository repository;
    public EntityResolutionService(NarrativeEntityRepository repository) { this.repository = repository; }

    public NarrativeEntity resolve(Story story, String name, EntityType type) {
        return repository.findByStoryIdAndNormalizedName(story.getId(), name.strip().toLowerCase())
            .orElseGet(() -> repository.save(new NarrativeEntity(story, name.strip(), type)));
    }
}
