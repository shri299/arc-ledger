package io.arcledger.repository;

import io.arcledger.domain.StoryInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.*;

public interface StoryInvitationRepository extends JpaRepository<StoryInvitation, UUID> {
    Optional<StoryInvitation> findByTokenHash(String tokenHash);
    Optional<StoryInvitation> findByIdAndStoryId(UUID id, UUID storyId);
    List<StoryInvitation> findByStoryIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(UUID storyId);
    List<StoryInvitation> findByStoryIdAndInvitedEmailAndAcceptedAtIsNullAndRevokedAtIsNull(UUID storyId, String invitedEmail);
    long deleteByExpiresAtBefore(Instant cutoff);
}
