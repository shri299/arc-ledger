package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class StoryService {
    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final StoryCollaborationService collaboration;
    public StoryService(StoryRepository storyRepository, ChapterRepository chapterRepository,
                        StoryCollaborationService collaboration) {
        this.storyRepository = storyRepository; this.chapterRepository = chapterRepository;
        this.collaboration = collaboration;
    }
    @Transactional public Story create(String title, String description) { return storyRepository.save(new Story(title, description)); }
    @Transactional public Story createForCurrentUser(String title, String description) {
        return collaboration.create(title, description);
    }
    @Transactional(readOnly = true) public Page<StoryCollaborationService.Access> listForCurrentUser(int page, int size) {
        return collaboration.list(page, size);
    }
    @Transactional(readOnly = true) public Story requireAccess(UUID id) {
        return collaboration.requireView(id).story();
    }
    @Transactional(readOnly = true) public Story requireEditAccess(UUID id) {
        return collaboration.requireEdit(id).story();
    }
    @Transactional(readOnly = true) public Story requireOwnerAccess(UUID id) {
        return collaboration.requireOwner(id).story();
    }
    @Transactional public Chapter addChapter(UUID storyId, int number, String title) {
        Story story = get(storyId); return chapterRepository.save(new Chapter(story, number, title));
    }
    @Transactional public Chapter addChapterForCurrentUser(UUID storyId, int number, String title) {
        Story story = requireEditAccess(storyId); return chapterRepository.save(new Chapter(story, number, title));
    }
    @Transactional(readOnly = true) public Page<Chapter> listChaptersForCurrentUser(UUID storyId, int page, int size) {
        requireAccess(storyId);
        return chapterRepository.findByStoryId(storyId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "number")));
    }
    public Story get(UUID id) { return storyRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Story not found: " + id)); }
    public Chapter getChapter(UUID storyId, UUID chapterId) { return chapterRepository.findByIdAndStoryId(chapterId, storyId)
        .orElseThrow(() -> new NoSuchElementException("Chapter not found in story: " + chapterId)); }
    @Transactional public Chapter lockChapter(UUID storyId, UUID chapterId) {
        return chapterRepository.findLocked(chapterId, storyId)
            .orElseThrow(() -> new NoSuchElementException("Chapter not found in story: " + chapterId));
    }
}
