package io.arcledger.service;

import io.arcledger.domain.ReconciliationAction;

public record ReconciliationDecision(ReconciliationAction action, String reason) {}
