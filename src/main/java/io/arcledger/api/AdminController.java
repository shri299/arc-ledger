package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.AppUser;
import io.arcledger.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminController {
    private final AdminAccountService admins;
    private final CurrentUserService currentUser;
    private final SecurityAuditService audit;

    public AdminController(AdminAccountService admins, CurrentUserService currentUser, SecurityAuditService audit) {
        this.admins = admins;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping("/accounts")
    public PageResponse<AdminAccountResponse> accounts(@RequestParam(defaultValue = "") @Size(max = 100) String query,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ApiModels.page(admins.accounts(query, page, size), this::account);
    }

    @PostMapping("/accounts/{userId}/suspend")
    public AdminAccountResponse suspend(@PathVariable UUID userId, HttpServletRequest request) {
        AppUser user = admins.suspend(userId);
        audit.record("ACCOUNT_SUSPENDED", "SUCCEEDED", currentUser.require().getId(), "ACCOUNT", userId, request);
        return account(user);
    }

    @PostMapping("/accounts/{userId}/restore")
    public AdminAccountResponse restore(@PathVariable UUID userId, HttpServletRequest request) {
        AppUser user = admins.restore(userId);
        audit.record("ACCOUNT_RESTORED", "SUCCEEDED", currentUser.require().getId(), "ACCOUNT", userId, request);
        return account(user);
    }

    @GetMapping("/audit-events")
    public PageResponse<AuditEventResponse> auditEvents(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ApiModels.page(admins.audits(page, size), item -> new AuditEventResponse(item.id(), item.actorId(),
            item.eventType(), item.outcome(), item.targetType(), item.targetId(), item.requestId(), item.createdAt()));
    }

    private AdminAccountResponse account(AppUser user) {
        return new AdminAccountResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isEmailVerified(),
            user.getAccountRole(), user.isEnabled(), user.getSuspendedAt(), user.getCreatedAt());
    }
}
