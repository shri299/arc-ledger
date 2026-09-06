package io.arcledger.api;

import io.arcledger.domain.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public final class ApiModels {
    private ApiModels() {}
    public record SignupRequest(@NotBlank @Size(max = 100) String displayName,
                                @NotBlank @Email @Size(max = 320) String email,
                                @NotBlank @Size(min = 12, max = 128) String password) {
        public SignupRequest {
            displayName = displayName == null ? null : displayName.strip();
            email = email == null ? null : email.strip();
        }
    }
    public record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                               @NotBlank @Size(max = 128) String password) {
        public LoginRequest {
            email = email == null ? null : email.strip();
        }
    }
    public record UserResponse(UUID id, String email, String displayName) {}
    public record CsrfResponse(String token) {}
    public record CreateStoryRequest(@NotBlank @Size(max = 120) String title,
                                     @Size(max = 2000) String description) {}
    public record StoryResponse(UUID id, String title, String description, Instant createdAt) {}
    public record CreateChapterRequest(@Positive int number, @NotBlank @Size(max = 255) String title) {}
    public record ChapterResponse(UUID id, UUID storyId, int number, String title, Instant createdAt) {}
    public record CreateSceneRequest(@Positive int sequence, @NotBlank @Size(max = 100_000) String rawText) {}
    public record SceneResponse(UUID id, UUID storyId, UUID chapterId, int sequence, ProcessingStatus status, Instant createdAt) {}
    public record FactResponse(String key, String value, KnowledgeKind knowledgeKind, UUID sourceSceneId) {}
    public record EntityResponse(UUID id, UUID storyId, String name, EntityType type, int latestVersion, Map<String, FactResponse> state) {}
    public record StateVersionResponse(UUID id, int version, UUID originatingSceneId, Instant timestamp,
                                       Map<String, Object> changedFacts, Map<String, Object> resultingState) {}
    public record ConsistencyResponse(UUID id, ValidationStatus status, Severity severity, UUID entityId,
                                      String description, String supportingEvidence, List<UUID> sourceSceneIds, Instant timestamp) {}
    public record AskResponse(String answer, String status, List<io.arcledger.service.NarrativeQuestionAnsweringService.Source> sources) {}
    public record ErrorResponse(String error, String message, Instant timestamp, String requestId) {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {}

    public static <S, T> PageResponse<T> page(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(source.getContent().stream().map(mapper).toList(), source.getNumber(), source.getSize(),
            source.getTotalElements(), source.getTotalPages(), source.hasNext());
    }
}
