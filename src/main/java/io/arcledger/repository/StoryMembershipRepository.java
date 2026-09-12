package io.arcledger.repository;

import io.arcledger.domain.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface StoryMembershipRepository extends JpaRepository<StoryMembership, StoryMembershipId> {
    @EntityGraph(attributePaths = {"story", "user"})
    Optional<StoryMembership> findByStoryIdAndUserId(UUID storyId, UUID userId);
    List<StoryMembership> findByStoryIdOrderByCreatedAt(UUID storyId);
    @EntityGraph(attributePaths = "story")
    Page<StoryMembership> findByUserId(UUID userId, Pageable pageable);

    @Query("select membership from StoryMembership membership join fetch membership.user where membership.story.id = :storyId order by membership.createdAt")
    List<StoryMembership> findMembers(@Param("storyId") UUID storyId);
}
