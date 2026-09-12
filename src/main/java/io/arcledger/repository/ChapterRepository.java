package io.arcledger.repository;
import io.arcledger.domain.Chapter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    Optional<Chapter> findByIdAndStoryId(UUID id, UUID storyId);
    Page<Chapter> findByStoryId(UUID storyId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select chapter from Chapter chapter where chapter.id = :id and chapter.story.id = :storyId")
    Optional<Chapter> findLocked(@Param("id") UUID id, @Param("storyId") UUID storyId);
}
