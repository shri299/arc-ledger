package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.ConsistencyResultRepository;
import io.arcledger.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class NarrativeProcessingPipeline {
    private final EntityExtractionService extractionService;
    private final EntityResolutionService resolutionService;
    private final EntityStateService stateService;
    private final StateReconciliationService reconciliationService;
    private final ConsistencyValidationService validationService;
    private final EntityStateVersionService versionService;
    private final ConsistencyResultRepository consistencyRepository;

    public NarrativeProcessingPipeline(EntityExtractionService extractionService, EntityResolutionService resolutionService,
        EntityStateService stateService, StateReconciliationService reconciliationService,
        ConsistencyValidationService validationService, EntityStateVersionService versionService,
        ConsistencyResultRepository consistencyRepository) {
        this.extractionService = extractionService; this.resolutionService = resolutionService; this.stateService = stateService;
        this.reconciliationService = reconciliationService; this.validationService = validationService;
        this.versionService = versionService; this.consistencyRepository = consistencyRepository;
    }

    @Transactional
    public void process(Scene scene) {
        ExtractionResult extraction = extractionService.extract(scene.getRawText());
        boolean hasIssues = false;
        for (ExtractedEntity extracted : extraction.entities()) {
            NarrativeEntity entity = resolutionService.resolve(scene.getStory(), extracted.name(), extracted.type());
            EntityState state = stateService.latest(entity);
            ConsistencyValidationService.ValidationOutcome validation = validationService.validate(scene, entity, state, extracted);
            hasIssues |= validation.hasIssues();
            Map<String, ExtractedFact> changes = new LinkedHashMap<>();
            for (ExtractedFact fact : extracted.facts()) {
                if (validation.rejectedFactKeys().contains(fact.key())) continue;
                ReconciliationDecision decision = reconciliationService.reconcile(fact.key(), state.facts().get(fact.key()), fact);
                switch (decision.action()) {
                    case ADD, UPDATE -> changes.put(fact.key(), fact);
                    case CONTRADICTION -> {
                        hasIssues = true;
                        EntityState.StateFact known = state.facts().get(fact.key());
                        consistencyRepository.save(new ConsistencyResult(scene.getStory(), scene, entity,
                            ValidationStatus.CONTRADICTION, Severity.HIGH,
                            "Canonical " + fact.key() + " conflicts with the new scene; state was not overwritten.",
                            "Known=" + (known == null ? "unknown" : known.value()) + "; incoming=" + fact.value() + "; " + decision.reason(),
                            known == null ? "" : known.sourceSceneId().toString()));
                    }
                    case UNCHANGED -> { }
                }
            }
            if (!changes.isEmpty()) versionService.createVersion(entity, scene, changes);
        }
        if (!hasIssues) consistencyRepository.save(new ConsistencyResult(scene.getStory(), scene, null,
            ValidationStatus.VALID, Severity.INFO, "No continuity conflicts were detected.", "", ""));
        scene.processed();
    }
}
