package io.arcledger.service;

import java.util.List;
import java.util.UUID;

public interface NarrativeQuestionAnsweringService {
    Answer answer(UUID storyId, String query);
    record Answer(String answer, String status, List<Source> sources) {}
    record Source(UUID sceneId, UUID entityId, UUID stateVersionId, String entityName, double relevance) {}
}
