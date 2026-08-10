package io.arcledger.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.*;
import io.arcledger.repository.*;
import io.arcledger.service.impl.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/stories/{storyId}/entities")
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
    public List<EntityResponse> list(@PathVariable UUID storyId) {
        storyService.get(storyId);
        return entityRepository.findByStoryIdOrderByNameAsc(storyId).stream().map(this::response).toList();
    }
    @GetMapping("/{entityId}")
    public EntityResponse get(@PathVariable UUID storyId, @PathVariable UUID entityId) { return response(find(storyId, entityId)); }
    @GetMapping("/{entityId}/history")
    public List<StateVersionResponse> history(@PathVariable UUID storyId, @PathVariable UUID entityId) {
        NarrativeEntity entity = find(storyId, entityId);
        return versionRepository.findByEntityIdOrderByVersionAsc(entity.getId()).stream().map(version ->
            new StateVersionResponse(version.getId(), version.getVersion(), version.getOriginatingScene().getId(), version.getCreatedAt(),
                map(version.getChangedFactsJson()), map(version.getResultingStateJson()))).toList();
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
