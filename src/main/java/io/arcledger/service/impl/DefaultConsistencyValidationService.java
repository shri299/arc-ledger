package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.ConsistencyResultRepository;
import io.arcledger.service.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DefaultConsistencyValidationService implements ConsistencyValidationService {
    private final ConsistencyResultRepository repository;
    private final NarrativeInferenceService inferenceService;
    public DefaultConsistencyValidationService(ConsistencyResultRepository repository,
                                               NarrativeInferenceService inferenceService) {
        this.repository = repository;
        this.inferenceService = inferenceService;
    }

    @Override
    public ValidationOutcome validate(Scene scene, NarrativeEntity entity, EntityState state, ExtractedEntity extracted) {
        Set<String> rejected = new HashSet<>();
        EntityState.StateFact leftArm = state.facts().get("leftArmStatus");
        for (ExtractedFact fact : extracted.facts()) {
            if (fact.knowledgeKind() != KnowledgeKind.FACT) {
                repository.save(new ConsistencyResult(scene.getStory(), scene, entity, ValidationStatus.INSUFFICIENT_CONTEXT,
                    Severity.LOW, "Ambiguous information was not promoted to canonical state.", fact.evidence(), ""));
                rejected.add(fact.key());
            }
            if (leftArm != null && Set.of("lost", "missing", "amputated").contains(leftArm.value().toLowerCase())
                && fact.key().equals("handUse") && fact.value().toLowerCase().contains("both")) {
                repository.save(new ConsistencyResult(scene.getStory(), scene, entity, ValidationStatus.CONTRADICTION,
                    Severity.HIGH, entity.getName() + " previously lost the left arm, but this scene describes use of both hands.",
                    "Known leftArmStatus=" + leftArm.value() + "; incoming evidence: " + fact.evidence(),
                    leftArm.sourceSceneId().toString()));
                rejected.add(fact.key());
            }
        }
        List<NarrativeInferenceService.ConsistencyAdvisory> advisories =
            inferenceService.analyzeConsistency(scene, entity.getName(), state, extracted);
        advisories.forEach(advisory ->
            repository.save(new ConsistencyResult(scene.getStory(), scene, entity,
                advisory.insufficientContext() ? ValidationStatus.INSUFFICIENT_CONTEXT : ValidationStatus.WARNING,
                advisory.insufficientContext() ? Severity.LOW : Severity.MEDIUM,
                advisory.description(), advisory.evidence(), sourceSceneIds(state))));
        return new ValidationOutcome(Set.copyOf(rejected), !rejected.isEmpty() || !advisories.isEmpty());
    }

    private String sourceSceneIds(EntityState state) {
        return state.facts().values().stream().map(EntityState.StateFact::sourceSceneId)
            .distinct().map(UUID::toString).reduce((left, right) -> left + "," + right).orElse("");
    }
}
