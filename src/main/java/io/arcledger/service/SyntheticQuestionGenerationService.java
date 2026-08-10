package io.arcledger.service;

import io.arcledger.domain.EntityState;
import io.arcledger.domain.EntityStateVersion;

public interface SyntheticQuestionGenerationService {
    void generateAndIndex(EntityStateVersion version, EntityState state);
}
