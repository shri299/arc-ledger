package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class SceneService {
    private final StoryService storyService;
    private final SceneRepository repository;
    private final NarrativeProcessingPipeline pipeline;
    public SceneService(StoryService storyService, SceneRepository repository, NarrativeProcessingPipeline pipeline) {
        this.storyService = storyService; this.repository = repository; this.pipeline = pipeline;
    }
    @Transactional
    public Scene create(UUID storyId, UUID chapterId, int sequence, String rawText) {
        Story story = storyService.get(storyId); Chapter chapter = storyService.getChapter(storyId, chapterId);
        Scene scene = repository.save(new Scene(story, chapter, sequence, rawText));
        pipeline.process(scene);
        return scene;
    }
    public Scene get(UUID storyId, UUID sceneId) { return repository.findByIdAndStoryId(sceneId, storyId)
        .orElseThrow(() -> new NoSuchElementException("Scene not found in story: " + sceneId)); }
}
