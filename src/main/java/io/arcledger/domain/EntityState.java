package io.arcledger.domain;

import java.util.Map;
import java.util.UUID;

public record EntityState(UUID entityId, int version, Map<String, StateFact> facts) {
    public record StateFact(String value, KnowledgeKind knowledgeKind, UUID sourceSceneId) {}
}
