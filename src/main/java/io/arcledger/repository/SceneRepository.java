package io.arcledger.repository;
import io.arcledger.domain.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SceneRepository extends JpaRepository<Scene, UUID> {
    Optional<Scene> findByIdAndStoryId(UUID id, UUID storyId);
}
