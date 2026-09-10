package io.arcledger.repository;
import io.arcledger.domain.Story;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface StoryRepository extends JpaRepository<Story, UUID> {
    Optional<Story> findByIdAndOwnerId(UUID id, UUID ownerId);
    Page<Story> findByOwnerId(UUID ownerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select story from Story story where story.id = :id")
    Optional<Story> findLocked(@Param("id") UUID id);
}
