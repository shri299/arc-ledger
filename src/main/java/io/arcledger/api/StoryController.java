package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.*;
import io.arcledger.service.impl.StoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/stories")
@Validated
public class StoryController {
    private final StoryService service;
    public StoryController(StoryService service) { this.service = service; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StoryResponse create(@Valid @RequestBody CreateStoryRequest request) {
        Story story = service.createForCurrentUser(request.title(), request.description());
        return new StoryResponse(story.getId(), story.getTitle(), story.getDescription(), story.getCreatedAt());
    }
    @GetMapping
    public PageResponse<StoryResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "24") @Min(1) @Max(100) int size) {
        return ApiModels.page(service.listForCurrentUser(page, size), story ->
            new StoryResponse(story.getId(), story.getTitle(), story.getDescription(), story.getCreatedAt()));
    }
    @PostMapping("/{storyId}/chapters") @ResponseStatus(HttpStatus.CREATED)
    public ChapterResponse addChapter(@PathVariable UUID storyId, @Valid @RequestBody CreateChapterRequest request) {
        Chapter chapter = service.addChapterForCurrentUser(storyId, request.number(), request.title());
        return new ChapterResponse(chapter.getId(), storyId, chapter.getNumber(), chapter.getTitle(), chapter.getCreatedAt());
    }
}
