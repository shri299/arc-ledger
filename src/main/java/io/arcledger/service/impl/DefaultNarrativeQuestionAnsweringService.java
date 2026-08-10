package io.arcledger.service.impl;

import io.arcledger.service.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DefaultNarrativeQuestionAnsweringService implements NarrativeQuestionAnsweringService {
    private final NarrativeRetrievalService retrievalService;
    public DefaultNarrativeQuestionAnsweringService(NarrativeRetrievalService retrievalService) { this.retrievalService = retrievalService; }

    @Override
    public Answer answer(UUID storyId, String query) {
        List<RetrievalHit> hits = retrievalService.retrieve(storyId, query, null, 5);
        if (hits.isEmpty() || hits.get(0).score() < 0.15)
            return new Answer("I do not have enough canonical story context to answer that question.",
                "INSUFFICIENT_CONTEXT", List.of());
        List<RetrievalHit> grounded = hits.stream().filter(hit -> hit.score() >= Math.max(0.15, hits.get(0).score() - 0.15)).limit(3).toList();
        String answer = grounded.stream().map(RetrievalHit::answer).distinct().reduce((a, b) -> a + " " + b).orElseThrow();
        List<Source> sources = grounded.stream().map(hit -> new Source(hit.sceneId(), hit.entityId(), hit.stateVersionId(), hit.entityName(), hit.score())).toList();
        return new Answer(answer, "GROUNDED", sources);
    }
}
