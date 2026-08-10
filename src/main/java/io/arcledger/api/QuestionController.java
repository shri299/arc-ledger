package io.arcledger.api;

import io.arcledger.api.ApiModels.AskResponse;
import io.arcledger.service.NarrativeQuestionAnsweringService;
import io.arcledger.service.impl.StoryService;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/stories/{storyId}/ask")
public class QuestionController {
    private final StoryService storyService;
    private final NarrativeQuestionAnsweringService service;
    public QuestionController(StoryService storyService, NarrativeQuestionAnsweringService service) {
        this.storyService = storyService; this.service = service;
    }
    @GetMapping
    public AskResponse ask(@PathVariable UUID storyId, @RequestParam String query) {
        storyService.get(storyId);
        NarrativeQuestionAnsweringService.Answer answer = service.answer(storyId, query);
        return new AskResponse(answer.answer(), answer.status(), answer.sources());
    }
}
