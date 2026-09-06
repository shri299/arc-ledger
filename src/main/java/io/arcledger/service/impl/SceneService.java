package io.arcledger.service.impl;

import io.arcledger.api.IdempotencyKeyConflictException;
import io.arcledger.domain.*;
import io.arcledger.repository.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class SceneService {
    private final StoryService storyService;
    private final SceneRepository repository;
    private final NarrativeProcessingPipeline pipeline;
    public SceneService(StoryService storyService, SceneRepository repository, NarrativeProcessingPipeline pipeline) {
        this.storyService = storyService; this.repository = repository; this.pipeline = pipeline;
    }

    public record IdempotentScene(Scene scene, boolean replayed) {}

    @Transactional
    public Scene create(UUID storyId, UUID chapterId, int sequence, String rawText) {
        Story story = storyService.get(storyId); Chapter chapter = storyService.getChapter(storyId, chapterId);
        Scene scene = repository.save(new Scene(story, chapter, sequence, rawText));
        pipeline.process(scene);
        return scene;
    }

    @Transactional
    public synchronized IdempotentScene createIdempotent(UUID storyId, UUID chapterId, int sequence, String rawText,
                                                          String idempotencyKey) {
        String requestHash = requestHash(storyId, chapterId, sequence, rawText);
        Optional<Scene> existing = repository.findByStoryIdAndIdempotencyKey(storyId, idempotencyKey);
        if (existing.isPresent()) {
            if (!requestHash.equals(existing.get().getRequestHash())) throw new IdempotencyKeyConflictException();
            return new IdempotentScene(existing.get(), true);
        }

        Story story = storyService.get(storyId);
        Chapter chapter = storyService.getChapter(storyId, chapterId);
        Scene scene = repository.save(new Scene(story, chapter, sequence, rawText, idempotencyKey, requestHash));
        pipeline.process(scene);
        return new IdempotentScene(scene, false);
    }

    public Scene get(UUID storyId, UUID sceneId) { return repository.findByIdAndStoryId(sceneId, storyId)
        .orElseThrow(() -> new NoSuchElementException("Scene not found in story: " + sceneId)); }

    private static String requestHash(UUID storyId, UUID chapterId, int sequence, String rawText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = storyId + "\n" + chapterId + "\n" + sequence + "\n" + rawText;
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
