package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.Scene;
import io.arcledger.repository.ConsistencyResultRepository;
import io.arcledger.service.impl.SceneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/stories/{storyId}")
public class SceneController {
    private final SceneService service;
    private final ConsistencyResultRepository consistencyRepository;
    public SceneController(SceneService service, ConsistencyResultRepository consistencyRepository) {
        this.service = service; this.consistencyRepository = consistencyRepository;
    }
    @PostMapping("/chapters/{chapterId}/scenes") @ResponseStatus(HttpStatus.CREATED)
    public SceneResponse create(@PathVariable UUID storyId, @PathVariable UUID chapterId,
                                @Valid @RequestBody CreateSceneRequest request) {
        Scene scene = service.create(storyId, chapterId, request.sequence(), request.rawText());
        return new SceneResponse(scene.getId(), storyId, chapterId, scene.getSequence(), scene.getProcessingStatus(), scene.getCreatedAt());
    }
    @GetMapping("/scenes/{sceneId}/consistency")
    public List<ConsistencyResponse> consistency(@PathVariable UUID storyId, @PathVariable UUID sceneId) {
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
}
