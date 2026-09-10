package io.arcledger.repository;
import io.arcledger.domain.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
public interface SceneRepository extends JpaRepository<Scene, UUID> {
    @EntityGraph(attributePaths = "chapter")
    Optional<Scene> findByIdAndStoryId(UUID id, UUID storyId);
    Optional<Scene> findByStoryIdAndIdempotencyKey(UUID storyId, String idempotencyKey);
}
