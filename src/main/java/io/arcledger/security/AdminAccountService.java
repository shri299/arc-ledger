package io.arcledger.security;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AdminAccountService {
    public record AuditView(UUID id, UUID actorId, String eventType, String outcome,
                            String targetType, UUID targetId, String requestId, Instant createdAt) {}

    private final AppUserRepository users;
    private final SecurityAuditEventRepository audits;
    private final CurrentUserService currentUser;
    private final JdbcTemplate jdbc;
    private final String configuredAdmins;

    public AdminAccountService(AppUserRepository users, SecurityAuditEventRepository audits,
        CurrentUserService currentUser, JdbcTemplate jdbc,
        @Value("${arcledger.security.admin-emails:}") String configuredAdmins) {
        this.users = users;
        this.audits = audits;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
        this.configuredAdmins = configuredAdmins;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileConfiguredAdmins() {
        Set<String> allowed = new HashSet<>(Arrays.stream(configuredAdmins.split(","))
            .map(AppUserDetailsService::normalize).filter(email -> !email.isBlank()).toList());
        Instant now = Instant.now();
        users.findByAccountRole(AccountRole.ADMIN).stream()
            .filter(user -> !allowed.contains(user.getEmail()))
            .forEach(user -> user.changeAccountRole(AccountRole.USER, now));
        allowed.forEach(email -> users.findByEmail(email)
            .ifPresent(user -> user.changeAccountRole(AccountRole.ADMIN, now)));
    }

    @Transactional(readOnly = true)
    public Page<AppUser> accounts(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (query == null || query.isBlank()) return users.findAll(pageable);
        String value = query.strip();
        return users.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(value, value, pageable);
    }

    @Transactional
    public AppUser suspend(UUID userId) {
        AppUser actor = currentUser.require();
        if (actor.getId().equals(userId)) throw new CollaborationConflictException("Administrators cannot suspend themselves.");
        AppUser user = users.findById(userId).orElseThrow(NoSuchElementException::new);
        user.suspend(Instant.now());
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", user.getEmail());
        return user;
    }

    @Transactional
    public AppUser restore(UUID userId) {
        AppUser user = users.findById(userId).orElseThrow(NoSuchElementException::new);
        user.restore(Instant.now());
        return user;
    }

    @Transactional(readOnly = true)
    public Page<AuditView> audits(int page, int size) {
        return audits.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(event -> new AuditView(event.getId(), event.getActor() == null ? null : event.getActor().getId(),
                event.getEventType(), event.getOutcome(), event.getTargetType(), event.getTargetId(),
                event.getRequestId(), event.getCreatedAt()));
    }
}
