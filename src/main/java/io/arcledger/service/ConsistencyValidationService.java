package io.arcledger.service;

import io.arcledger.domain.*;
import java.util.Set;

public interface ConsistencyValidationService {
    ValidationOutcome validate(Scene scene, NarrativeEntity entity, EntityState state, ExtractedEntity extracted);
    record ValidationOutcome(Set<String> rejectedFactKeys, boolean hasIssues) {}
}
