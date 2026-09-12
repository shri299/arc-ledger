package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.*;
import io.arcledger.security.*;
import io.arcledger.service.impl.StoryCollaborationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class CollaborationController {
    private final StoryCollaborationService collaboration;
    private final AccountMailer mailer;
    private final CurrentUserService currentUser;
    private final SecurityAuditService audit;

    public CollaborationController(StoryCollaborationService collaboration, AccountMailer mailer,
                                   CurrentUserService currentUser, SecurityAuditService audit) {
        this.collaboration = collaboration;
        this.mailer = mailer;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PostMapping("/stories/{storyId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public StoryInvitationResponse invite(@PathVariable UUID storyId,
                                          @Valid @RequestBody StoryInvitationRequest body,
                                          HttpServletRequest request) {
        StoryInvitation invitation = collaboration.invite(storyId, body.email(), body.role());
        audit.record("STORY_INVITATION_CREATED", "SUCCEEDED", currentUser.require().getId(), "STORY", storyId, request);
        return invitation(invitation);
    }

    @PostMapping("/invitations/accept")
    public StoryMemberResponse accept(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        StoryMembership membership = collaboration.accept(body.token());
        audit.record("STORY_INVITATION_ACCEPTED", "SUCCEEDED", currentUser.require().getId(), request);
        return member(membership);
    }

    @GetMapping("/stories/{storyId}/collaborators")
    public StoryAccessResponse collaborators(@PathVariable UUID storyId) {
        return new StoryAccessResponse(
            collaboration.members(storyId).stream().map(this::member).toList(),
            collaboration.pendingInvitations(storyId).stream().map(this::invitation).toList(),
            mailer.isConfigured());
    }

    @PostMapping("/stories/{storyId}/collaborators/{userId}/role")
    public StoryMemberResponse changeRole(@PathVariable UUID storyId, @PathVariable UUID userId,
                                          @Valid @RequestBody StoryRoleRequest body, HttpServletRequest request) {
        StoryMembership membership = collaboration.changeRole(storyId, userId, body.role());
        audit.record("STORY_MEMBER_ROLE_CHANGED", "SUCCEEDED", currentUser.require().getId(), "STORY", storyId, request);
        return member(membership);
    }

    @PostMapping("/stories/{storyId}/collaborators/{userId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID storyId, @PathVariable UUID userId, HttpServletRequest request) {
        collaboration.removeMember(storyId, userId);
        audit.record("STORY_MEMBER_REMOVED", "SUCCEEDED", currentUser.require().getId(), "STORY", storyId, request);
    }

    @PostMapping("/stories/{storyId}/invitations/{invitationId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable UUID storyId, @PathVariable UUID invitationId,
                                 HttpServletRequest request) {
        collaboration.revokeInvitation(storyId, invitationId);
        audit.record("STORY_INVITATION_REVOKED", "SUCCEEDED", currentUser.require().getId(), "STORY", storyId, request);
    }

    private StoryMemberResponse member(StoryMembership membership) {
        return new StoryMemberResponse(membership.getUser().getId(), membership.getUser().getEmail(),
            membership.getUser().getDisplayName(), membership.getRole(), membership.getCreatedAt());
    }

    private StoryInvitationResponse invitation(StoryInvitation invitation) {
        return new StoryInvitationResponse(invitation.getId(), invitation.getInvitedEmail(), invitation.getRole(),
            invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
