package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import io.arcledger.security.CurrentUserService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StoryService {
    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final CurrentUserService currentUserService;
    public StoryService(StoryRepository storyRepository, ChapterRepository chapterRepository, CurrentUserService currentUserService) {
        this.storyRepository = storyRepository; this.chapterRepository = chapterRepository; this.currentUserService = currentUserService;
    }
    @Transactional public Story create(String title, String description) { return storyRepository.save(new Story(title, description)); }
    @Transactional public Story createForCurrentUser(String title, String description) {
        return storyRepository.save(new Story(title, description, currentUserService.require()));
    }
    @Transactional(readOnly = true) public Page<Story> listForCurrentUser(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return storyRepository.findByOwnerId(currentUserService.require().getId(), pageable);
    }
    @Transactional(readOnly = true) public Story requireAccess(UUID id) {
        UUID ownerId = currentUserService.require().getId();
        return storyRepository.findByIdAndOwnerId(id, ownerId)
            .orElseThrow(() -> new NoSuchElementException("Story not found: " + id));
    }
    @Transactional public Chapter addChapter(UUID storyId, int number, String title) {
        Story story = get(storyId); return chapterRepository.save(new Chapter(story, number, title));
    }
    @Transactional public Chapter addChapterForCurrentUser(UUID storyId, int number, String title) {
        Story story = requireAccess(storyId); return chapterRepository.save(new Chapter(story, number, title));
    }
    public Story get(UUID id) { return storyRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Story not found: " + id)); }
    public Chapter getChapter(UUID storyId, UUID chapterId) { return chapterRepository.findByIdAndStoryId(chapterId, storyId)
        .orElseThrow(() -> new NoSuchElementException("Chapter not found in story: " + chapterId)); }
}
