package io.arcledger.service;

import java.util.UUID;

public record RetrievalHit(UUID questionId, UUID entityId, UUID stateVersionId, UUID sceneId,
                           String entityName, String question, String answer, boolean current,
                           int version, double score) {}
