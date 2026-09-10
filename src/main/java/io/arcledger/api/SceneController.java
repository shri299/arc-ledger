package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.Scene;
import io.arcledger.repository.ConsistencyResultRepository;
import io.arcledger.service.impl.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/stories/{storyId}")
@Validated
public class SceneController {
    private final SceneService service;
    private final StoryService storyService;
    private final ConsistencyResultRepository consistencyRepository;
    public SceneController(SceneService service, StoryService storyService, ConsistencyResultRepository consistencyRepository) {
        this.service = service; this.storyService = storyService; this.consistencyRepository = consistencyRepository;
    }
    @PostMapping("/chapters/{chapterId}/scenes")
    public ResponseEntity<SceneResponse> create(
                                @PathVariable UUID storyId, @PathVariable UUID chapterId,
                                @RequestHeader("Idempotency-Key")
                                @Pattern(regexp = "[A-Za-z0-9._:-]{8,128}") String idempotencyKey,
                                @Valid @RequestBody CreateSceneRequest request) {
        storyService.requireAccess(storyId);
        SceneService.IdempotentScene result = service.createIdempotent(
            storyId, chapterId, request.sequence(), request.rawText(), idempotencyKey);
        Scene scene = result.scene();
        SceneResponse body = new SceneResponse(
            scene.getId(), storyId, chapterId, scene.getSequence(), scene.getProcessingStatus(), scene.getCreatedAt());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.RETRY_AFTER, "2")
            .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
            .body(body);
    }
    @GetMapping("/scenes/{sceneId}")
    public SceneResponse get(@PathVariable UUID storyId, @PathVariable UUID sceneId) {
        storyService.requireAccess(storyId);
        Scene scene = service.get(storyId, sceneId);
        return response(storyId, scene);
    }

    @PostMapping("/scenes/{sceneId}/retry")
    public ResponseEntity<SceneResponse> retry(@PathVariable UUID storyId, @PathVariable UUID sceneId) {
        storyService.requireAccess(storyId);
        Scene scene = service.retry(storyId, sceneId);
        return ResponseEntity.accepted().header(HttpHeaders.RETRY_AFTER, "2").body(response(storyId, scene));
    }
    @GetMapping("/scenes/{sceneId}/consistency")
    public List<ConsistencyResponse> consistency(@PathVariable UUID storyId, @PathVariable UUID sceneId) {
        storyService.requireAccess(storyId);
        service.get(storyId, sceneId);
        return consistencyRepository.findBySceneIdOrderByCreatedAtAsc(sceneId).stream().map(result ->
            new ConsistencyResponse(result.getId(), result.getStatus(), result.getSeverity(),
                result.getEntity() == null ? null : result.getEntity().getId(), result.getDescription(),
                result.getSupportingEvidence(), parseIds(result.getSourceSceneIds()), result.getCreatedAt())).toList();
    }
    private List<UUID> parseIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isBlank()).map(UUID::fromString).toList();
    }
    private SceneResponse response(UUID storyId, Scene scene) {
        return new SceneResponse(scene.getId(), storyId, scene.getChapter().getId(), scene.getSequence(),
            scene.getProcessingStatus(), scene.getCreatedAt());
    }
}
