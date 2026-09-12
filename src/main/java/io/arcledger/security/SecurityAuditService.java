package io.arcledger.security;

import io.arcledger.api.RequestIds;
import io.arcledger.domain.SecurityAuditEvent;
import io.arcledger.repository.AppUserRepository;
import io.arcledger.repository.SecurityAuditEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.UUID;

@Service
public class SecurityAuditService {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private final AppUserRepository users;
    private final SecurityAuditEventRepository events;
    private final PrivacyHashService privacyHashes;

    public SecurityAuditService(AppUserRepository users, SecurityAuditEventRepository events,
                                PrivacyHashService privacyHashes) {
        this.users = users;
        this.events = events;
        this.privacyHashes = privacyHashes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String event, String outcome, UUID actorId, HttpServletRequest request) {
        record(event, outcome, actorId, null, null, request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String event, String outcome, UUID actorId, String targetType, UUID targetId,
                       HttpServletRequest request) {
        log.info("security_event={} outcome={} actor_id={} request_id={}",
            event, outcome, actorId == null ? "anonymous" : actorId, RequestIds.current(request));
        try {
            events.save(new SecurityAuditEvent(actorId == null ? null : users.findById(actorId).orElse(null),
                event, outcome, targetType, targetId, RequestIds.current(request), privacyHashes.hash(request.getRemoteAddr())));
        } catch (RuntimeException exception) {
            log.warn("security_audit_persistence=FAILED exception_type={}", exception.getClass().getName());
        }
    }

    @Scheduled(cron = "0 40 3 * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prune() { events.deleteByCreatedAtBefore(Instant.now().minus(Duration.ofDays(180))); }

    public UUID actorId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return users.findByEmail(AppUserDetailsService.normalize(authentication.getName()))
            .map(user -> user.getId()).orElse(null);
    }
}
