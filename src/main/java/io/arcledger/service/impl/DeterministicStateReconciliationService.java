package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.service.*;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class DeterministicStateReconciliationService implements StateReconciliationService {
    private static final Set<String> ABSENT = Set.of("lost", "missing", "amputated", "destroyed", "dead");
    private static final Set<String> PRESENT = Set.of("healthy", "present", "intact", "alive", "both hands");

    @Override
    public ReconciliationDecision reconcile(String key, EntityState.StateFact existing, ExtractedFact incoming) {
        if (incoming.knowledgeKind() != KnowledgeKind.FACT)
            return new ReconciliationDecision(ReconciliationAction.UNCHANGED, "Only explicit FACT knowledge can mutate canonical state");
        if (existing == null) return new ReconciliationDecision(ReconciliationAction.ADD, "No canonical value exists");
        if (normalize(existing.value()).equals(normalize(incoming.value())))
            return new ReconciliationDecision(ReconciliationAction.UNCHANGED, "Value already matches canonical state");
        if (isIrreversibleConflict(key, existing.value(), incoming.value()))
            return new ReconciliationDecision(ReconciliationAction.CONTRADICTION, "Incoming value reverses an irreversible state without an explained change");
        if (incoming.intent() == FactIntent.CHANGE)
            return new ReconciliationDecision(ReconciliationAction.UPDATE, "Scene explicitly describes a state change");
        return new ReconciliationDecision(ReconciliationAction.CONTRADICTION, "Assertion conflicts with current canonical state");
    }

    private boolean isIrreversibleConflict(String key, String before, String after) {
        String normalizedKey = key.toLowerCase();
        return (normalizedKey.contains("status") || normalizedKey.contains("condition"))
            && ABSENT.contains(normalize(before)) && PRESENT.contains(normalize(after));
    }
    private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(); }
}
