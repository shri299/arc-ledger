package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.*;
import io.arcledger.service.impl.StoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/stories")
public class StoryController {
    private final StoryService service;
    public StoryController(StoryService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StoryResponse create(@Valid @RequestBody CreateStoryRequest request) {
        Story story = service.create(request.title(), request.description());
        return new StoryResponse(story.getId(), story.getTitle(), story.getDescription(), story.getCreatedAt());
    }
    @PostMapping("/{storyId}/chapters") @ResponseStatus(HttpStatus.CREATED)
    public ChapterResponse addChapter(@PathVariable UUID storyId, @Valid @RequestBody CreateChapterRequest request) {
        Chapter chapter = service.addChapter(storyId, request.number(), request.title());
        return new ChapterResponse(chapter.getId(), storyId, chapter.getNumber(), chapter.getTitle(), chapter.getCreatedAt());
    }
}
