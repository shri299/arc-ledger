package io.arcledger.api;

import io.arcledger.api.ApiModels.*;
import io.arcledger.domain.AppUser;
import io.arcledger.security.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserAccountService accounts;
    private final CurrentUserService currentUser;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityAuditService audit;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(UserAccountService accounts, CurrentUserService currentUser,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy,
                          CsrfTokenRepository csrfTokenRepository,
                          SecurityAuditService audit) {
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfTokenRepository = csrfTokenRepository;
        this.audit = audit;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request,
                               HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        try {
            AppUser user = accounts.register(request.email(), request.password(), request.displayName());
            authenticate(request.email(), request.password(), servletRequest, servletResponse);
            audit.record("SIGNUP", "SUCCEEDED", user.getId(), servletRequest);
            return response(user);
        } catch (EmailAlreadyRegisteredException exception) {
            audit.record("SIGNUP", "REJECTED", null, servletRequest);
            throw exception;
        }
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        try {
            authenticate(request.email(), request.password(), servletRequest, servletResponse);
            AppUser user = currentUser.require();
            audit.record("LOGIN", "SUCCEEDED", user.getId(), servletRequest);
            return response(user);
        } catch (AuthenticationException exception) {
            audit.record("LOGIN", "REJECTED", null, servletRequest);
            throw exception;
        }
    }

    @GetMapping("/me")
    public UserResponse me() {
        return response(currentUser.require());
    }

    private void authenticate(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(AppUserDetailsService.normalize(email), password));
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(null, request, response);
        CsrfToken freshToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(freshToken, request, response);
    }

    private static UserResponse response(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
