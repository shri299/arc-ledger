package io.arcledger.service;

import io.arcledger.domain.*;
import io.arcledger.service.impl.DeterministicStateReconciliationService;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class StateReconciliationServiceTest {
    private final StateReconciliationService service = new DeterministicStateReconciliationService();

    @Test void addsWhenNoCanonicalValueExists() {
        assertThat(service.reconcile("location", null, fact("London", FactIntent.ASSERTION, KnowledgeKind.FACT)).action())
            .isEqualTo(ReconciliationAction.ADD);
    }
    @Test void keepsIdenticalFactsUnchanged() {
        assertThat(service.reconcile("hairColor", state("Black"), fact("black", FactIntent.ASSERTION, KnowledgeKind.FACT)).action())
            .isEqualTo(ReconciliationAction.UNCHANGED);
    }
    @Test void appliesExplicitChanges() {
        assertThat(service.reconcile("location", state("London"), fact("Paris", FactIntent.CHANGE, KnowledgeKind.FACT)).action())
            .isEqualTo(ReconciliationAction.UPDATE);
    }
    @Test void rejectsConflictingAssertions() {
        assertThat(service.reconcile("hairColor", state("black"), fact("blonde", FactIntent.ASSERTION, KnowledgeKind.FACT)).action())
            .isEqualTo(ReconciliationAction.CONTRADICTION);
    }
    @Test void neverPromotesInferenceToCanonicalState() {
        assertThat(service.reconcile("goal", null, fact("escape", FactIntent.ASSERTION, KnowledgeKind.INFERENCE)).action())
            .isEqualTo(ReconciliationAction.UNCHANGED);
    }
    @Test void rejectsReversalOfIrreversibleStatus() {
        assertThat(service.reconcile("leftArmStatus", state("lost"), fact("healthy", FactIntent.CHANGE, KnowledgeKind.FACT)).action())
            .isEqualTo(ReconciliationAction.CONTRADICTION);
    }
    private EntityState.StateFact state(String value) { return new EntityState.StateFact(value, KnowledgeKind.FACT, UUID.randomUUID()); }
    private ExtractedFact fact(String value, FactIntent intent, KnowledgeKind kind) { return new ExtractedFact("key", value, kind, intent, "evidence"); }
}
