package io.arcledger.repository;
import io.arcledger.domain.NarrativeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NarrativeEntityRepository extends JpaRepository<NarrativeEntity, UUID> {
    Optional<NarrativeEntity> findByStoryIdAndNormalizedName(UUID storyId, String normalizedName);
    List<NarrativeEntity> findByStoryIdOrderByNameAsc(UUID storyId);
    Optional<NarrativeEntity> findByIdAndStoryId(UUID id, UUID storyId);
}
