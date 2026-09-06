package io.arcledger.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.*;
import io.arcledger.repository.*;
import io.arcledger.service.impl.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/stories/{storyId}/entities")
@Validated
public class EntityController {
    private final StoryService storyService;
    private final NarrativeEntityRepository entityRepository;
    private final EntityStateVersionRepository versionRepository;
    private final EntityStateService stateService;
    private final ObjectMapper objectMapper;
    public EntityController(StoryService storyService, NarrativeEntityRepository entityRepository,
                            EntityStateVersionRepository versionRepository, EntityStateService stateService, ObjectMapper objectMapper) {
        this.storyService = storyService; this.entityRepository = entityRepository; this.versionRepository = versionRepository;
        this.stateService = stateService; this.objectMapper = objectMapper;
    }
    @GetMapping
    public PageResponse<EntityResponse> list(@PathVariable UUID storyId,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        storyService.requireAccess(storyId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return ApiModels.page(entityRepository.findByStoryId(storyId, pageable), this::response);
    }
    @GetMapping("/{entityId}")
    public EntityResponse get(@PathVariable UUID storyId, @PathVariable UUID entityId) {
        storyService.requireAccess(storyId);
        return response(find(storyId, entityId));
    }
    @GetMapping("/{entityId}/history")
    public PageResponse<StateVersionResponse> history(@PathVariable UUID storyId, @PathVariable UUID entityId,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        storyService.requireAccess(storyId);
        NarrativeEntity entity = find(storyId, entityId);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "version"));
        return ApiModels.page(versionRepository.findByEntityId(entity.getId(), pageable), version ->
            new StateVersionResponse(version.getId(), version.getVersion(), version.getOriginatingScene().getId(), version.getCreatedAt(),
                map(version.getChangedFactsJson()), map(version.getResultingStateJson())));
    }
    private NarrativeEntity find(UUID storyId, UUID entityId) { return entityRepository.findByIdAndStoryId(entityId, storyId)
        .orElseThrow(() -> new NoSuchElementException("Entity not found in story: " + entityId)); }
    private EntityResponse response(NarrativeEntity entity) {
        EntityState state = stateService.latest(entity); Map<String, FactResponse> facts = new LinkedHashMap<>();
        state.facts().forEach((key, fact) -> facts.put(key, new FactResponse(key, fact.value(), fact.knowledgeKind(), fact.sourceSceneId())));
        return new EntityResponse(entity.getId(), entity.getStory().getId(), entity.getName(), entity.getType(), entity.getLatestVersion(), facts);
    }
    private Map<String, Object> map(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Invalid state history JSON", exception); }
    }
}
