package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StoryService {
    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    public StoryService(StoryRepository storyRepository, ChapterRepository chapterRepository) {
        this.storyRepository = storyRepository; this.chapterRepository = chapterRepository;
    }
    @Transactional public Story create(String title, String description) { return storyRepository.save(new Story(title, description)); }
    @Transactional public Chapter addChapter(UUID storyId, int number, String title) {
        Story story = get(storyId); return chapterRepository.save(new Chapter(story, number, title));
    }
    public Story get(UUID id) { return storyRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Story not found: " + id)); }
    public Chapter getChapter(UUID storyId, UUID chapterId) { return chapterRepository.findByIdAndStoryId(chapterId, storyId)
        .orElseThrow(() -> new NoSuchElementException("Chapter not found in story: " + chapterId)); }
}
