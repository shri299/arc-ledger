package io.arcledger.security;

import io.arcledger.api.RequestIds;
import io.arcledger.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SecurityAuditService {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private final AppUserRepository users;

    public SecurityAuditService(AppUserRepository users) {
        this.users = users;
    }

    public void record(String event, String outcome, UUID actorId, HttpServletRequest request) {
        log.info("security_event={} outcome={} actor_id={} request_id={}",
            event, outcome, actorId == null ? "anonymous" : actorId, RequestIds.current(request));
    }

    public UUID actorId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        return users.findByEmail(AppUserDetailsService.normalize(authentication.getName()))
            .map(user -> user.getId()).orElse(null);
    }
}
