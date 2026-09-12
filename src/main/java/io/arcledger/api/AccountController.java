package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/account")
public class AccountController {
    private final AccountLifecycleService lifecycle;
    private final SessionManagementService sessions;
    private final AccountDataService data;
    private final CurrentUserService currentUser;
    private final SecurityAuditService audit;

    public AccountController(AccountLifecycleService lifecycle, SessionManagementService sessions,
                             AccountDataService data, CurrentUserService currentUser, SecurityAuditService audit) {
        this.lifecycle = lifecycle;
        this.sessions = sessions;
        this.data = data;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(HttpServletRequest request) {
        sessions.record(currentUser.require(), request);
        return sessions.list(request).stream().map(item -> new SessionResponse(item.id(), item.device(),
            item.createdAt(), item.lastSeenAt(), item.expiresAt(), item.current())).toList();
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID sessionId, HttpServletRequest request) {
        UUID actorId = currentUser.require().getId();
        boolean current = sessions.revoke(sessionId, request);
        audit.record("SESSION_REVOKED", "SUCCEEDED", actorId, request);
        if (current && request.getSession(false) != null) request.getSession(false).invalidate();
    }

    @PostMapping("/sessions/revoke-others")
    public MessageResponse revokeOthers(HttpServletRequest request) {
        UUID actorId = currentUser.require().getId();
        int count = sessions.revokeOthers(request);
        audit.record("OTHER_SESSIONS_REVOKED", "SUCCEEDED", actorId, request);
        return new MessageResponse(count + " other session(s) revoked.");
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        UUID actorId = currentUser.require().getId();
        lifecycle.changePassword(body.currentPassword(), body.newPassword());
        audit.record("PASSWORD_CHANGED", "SUCCEEDED", actorId, request);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }

    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse recoveryCodes(@Valid @RequestBody PasswordConfirmationRequest body,
                                                HttpServletRequest request) {
        UUID actorId = currentUser.require().getId();
        List<String> codes = lifecycle.regenerateRecoveryCodes(body.currentPassword());
        audit.record("RECOVERY_CODES_REGENERATED", "SUCCEEDED", actorId, request);
        return new RecoveryCodesResponse(codes);
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> export() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=arcledger-account-export.json")
            .body(data.export());
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@Valid @RequestBody DeleteAccountRequest body, HttpServletRequest request) {
        data.delete(body.currentPassword());
        audit.record("ACCOUNT_DELETED", "SUCCEEDED", null, request);
        if (request.getSession(false) != null) request.getSession(false).invalidate();
    }
}
