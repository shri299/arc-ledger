package io.arcledger.repository;
import io.arcledger.domain.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    Optional<Chapter> findByIdAndStoryId(UUID id, UUID storyId);
}
