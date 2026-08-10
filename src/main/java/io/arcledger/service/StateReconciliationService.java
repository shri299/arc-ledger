package io.arcledger.service;

import io.arcledger.domain.EntityState;

public interface StateReconciliationService {
    ReconciliationDecision reconcile(String key, EntityState.StateFact existing, ExtractedFact incoming);
}
