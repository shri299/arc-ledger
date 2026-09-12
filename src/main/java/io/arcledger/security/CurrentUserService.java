package io.arcledger.security;

import io.arcledger.domain.AppUser;
import io.arcledger.repository.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {
    private final AppUserRepository repository;
    private final boolean requireEmailVerification;

    public CurrentUserService(AppUserRepository repository,
                              @Value("${arcledger.security.require-email-verification:false}") boolean requireEmailVerification) {
        this.repository = repository;
        this.requireEmailVerification = requireEmailVerification;
    }

    @Transactional(readOnly = true)
    public AppUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("Authentication is required.");
        }
        return repository.findByEmail(AppUserDetailsService.normalize(authentication.getName()))
            .orElseThrow(() -> new AccessDeniedException("The authenticated account no longer exists."));
    }

    @Transactional(readOnly = true)
    public AppUser requireVerified() {
        AppUser user = require();
        if (requireEmailVerification && !user.isEmailVerified()) {
            throw new EmailVerificationRequiredException();
        }
        return user;
    }
}
