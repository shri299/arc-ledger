package io.arcledger.service.impl;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import io.arcledger.security.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.slf4j.*;

import java.time.*;
import java.util.*;

@Service
public class StoryCollaborationService {
    private static final Logger log = LoggerFactory.getLogger(StoryCollaborationService.class);
    public record Access(Story story, StoryRole role) {}

    private final StoryRepository stories;
    private final StoryMembershipRepository memberships;
    private final StoryInvitationRepository invitations;
    private final AppUserRepository users;
    private final CurrentUserService currentUser;
    private final SecureTokenService tokens;
    private final AccountMailer mailer;

    public StoryCollaborationService(StoryRepository stories, StoryMembershipRepository memberships,
        StoryInvitationRepository invitations, AppUserRepository users, CurrentUserService currentUser,
        SecureTokenService tokens, AccountMailer mailer) {
        this.stories = stories;
        this.memberships = memberships;
        this.invitations = invitations;
        this.users = users;
        this.currentUser = currentUser;
        this.tokens = tokens;
        this.mailer = mailer;
    }

    @Transactional
    public Story create(String title, String description) {
        AppUser owner = currentUser.requireVerified();
        Story story = stories.save(new Story(title, description, owner));
        memberships.save(new StoryMembership(story, owner, StoryRole.OWNER, owner));
        return story;
    }

    @Transactional(readOnly = true)
    public Page<Access> list(int page, int size) {
        AppUser user = currentUser.require();
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return memberships.findByUserId(user.getId(), request).map(item -> new Access(item.getStory(), item.getRole()));
    }

    @Transactional(readOnly = true)
    public Access requireView(UUID storyId) { return require(storyId); }

    @Transactional(readOnly = true)
    public Access requireEdit(UUID storyId) {
        currentUser.requireVerified();
        Access access = require(storyId);
        if (!access.role().canEdit()) throw notFound(storyId);
        return access;
    }

    @Transactional(readOnly = true)
    public Access requireOwner(UUID storyId) {
        currentUser.requireVerified();
        Access access = require(storyId);
        if (!access.role().canManage()) throw notFound(storyId);
        return access;
    }

    @Transactional
    public StoryInvitation invite(UUID storyId, String email, StoryRole role) {
        if (role == StoryRole.OWNER) throw new CollaborationConflictException("Ownership cannot be assigned by invitation.");
        Access access = requireOwner(storyId);
        AppUser inviter = currentUser.requireVerified();
        String normalizedEmail = AppUserDetailsService.normalize(email);
        users.findByEmail(normalizedEmail).ifPresent(invited -> memberships.findByStoryIdAndUserId(storyId, invited.getId())
            .ifPresent(existing -> { throw new CollaborationConflictException("That account already has access."); }));
        invitations.findByStoryIdAndInvitedEmailAndAcceptedAtIsNullAndRevokedAtIsNull(storyId, normalizedEmail)
            .forEach(existing -> existing.revoke(Instant.now()));
        String rawToken = tokens.generate();
        StoryInvitation invitation = invitations.save(new StoryInvitation(access.story(), normalizedEmail, role,
            tokens.hash(rawToken), inviter, Instant.now().plus(Duration.ofDays(7))));
        sendAfterCommit(() -> mailer.sendStoryInvitation(
            normalizedEmail, inviter.getDisplayName(), access.story().getTitle(), rawToken));
        return invitation;
    }

    @Transactional
    public StoryMembership accept(String rawToken) {
        AppUser user = currentUser.requireVerified();
        StoryInvitation invitation = invitations.findByTokenHash(tokens.hash(rawToken))
            .filter(value -> value.isUsableAt(Instant.now()))
            .filter(value -> value.getInvitedEmail().equals(user.getEmail()))
            .orElseThrow(InvalidAccountTokenException::new);
        StoryMembership membership = memberships.findByStoryIdAndUserId(invitation.getStory().getId(), user.getId())
            .orElseGet(() -> memberships.save(new StoryMembership(
                invitation.getStory(), user, invitation.getRole(), invitation.getInvitedBy())));
        if (membership.getRole() != StoryRole.OWNER) membership.changeRole(invitation.getRole());
        invitation.accept(Instant.now());
        return membership;
    }

    @Transactional(readOnly = true)
    public List<StoryMembership> members(UUID storyId) {
        requireOwner(storyId);
        return memberships.findMembers(storyId);
    }

    @Transactional(readOnly = true)
    public List<StoryInvitation> pendingInvitations(UUID storyId) {
        requireOwner(storyId);
        return invitations.findByStoryIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(storyId);
    }

    @Transactional
    public StoryMembership changeRole(UUID storyId, UUID userId, StoryRole role) {
        requireOwner(storyId);
        if (role == StoryRole.OWNER) throw new CollaborationConflictException("Story ownership cannot be transferred here.");
        StoryMembership membership = memberships.findByStoryIdAndUserId(storyId, userId)
            .orElseThrow(() -> notFound(storyId));
        if (membership.getRole() == StoryRole.OWNER) throw new CollaborationConflictException("The owner role cannot be changed.");
        membership.changeRole(role);
        return membership;
    }

    @Transactional
    public void removeMember(UUID storyId, UUID userId) {
        requireOwner(storyId);
        StoryMembership membership = memberships.findByStoryIdAndUserId(storyId, userId)
            .orElseThrow(() -> notFound(storyId));
        if (membership.getRole() == StoryRole.OWNER) throw new CollaborationConflictException("The story owner cannot be removed.");
        memberships.delete(membership);
    }

    @Transactional
    public void revokeInvitation(UUID storyId, UUID invitationId) {
        requireOwner(storyId);
        StoryInvitation invitation = invitations.findByIdAndStoryId(invitationId, storyId)
            .orElseThrow(() -> notFound(storyId));
        invitation.revoke(Instant.now());
    }

    private Access require(UUID storyId) {
        AppUser user = currentUser.require();
        StoryMembership membership = memberships.findByStoryIdAndUserId(storyId, user.getId())
            .orElseThrow(() -> notFound(storyId));
        return new Access(membership.getStory(), membership.getRole());
    }

    private void sendAfterCommit(Runnable delivery) {
        if (!mailer.isConfigured()) return;
        Runnable safeDelivery = () -> {
            try {
                delivery.run();
            } catch (RuntimeException exception) {
                log.warn("Story invitation delivery failed exception_type={}", exception.getClass().getName());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeDelivery.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { safeDelivery.run(); }
        });
    }

    private static NoSuchElementException notFound(UUID storyId) {
        return new NoSuchElementException("Story not found: " + storyId);
    }
}
