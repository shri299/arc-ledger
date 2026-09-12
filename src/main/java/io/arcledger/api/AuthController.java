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
    private final AccountLifecycleService lifecycle;
    private final SessionManagementService sessions;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(UserAccountService accounts, CurrentUserService currentUser,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy,
                          CsrfTokenRepository csrfTokenRepository,
                          SecurityAuditService audit, AccountLifecycleService lifecycle,
                          SessionManagementService sessions) {
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.csrfTokenRepository = csrfTokenRepository;
        this.audit = audit;
        this.lifecycle = lifecycle;
        this.sessions = sessions;
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
            lifecycle.beginVerification(user);
            sessions.record(user, servletRequest);
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
            sessions.record(user, servletRequest);
            audit.record("LOGIN", "SUCCEEDED", user.getId(), servletRequest);
            return response(user);
        } catch (AuthenticationException exception) {
            audit.record("LOGIN", "REJECTED", null, servletRequest);
            throw exception;
        }
    }

    @GetMapping("/me")
    public UserResponse me(HttpServletRequest request) {
        AppUser user = currentUser.require();
        sessions.record(user, request);
        return response(user);
    }

    @PostMapping("/verification/request")
    public MessageResponse requestVerification(HttpServletRequest request) {
        lifecycle.requestVerification();
        audit.record("EMAIL_VERIFICATION_REQUESTED", "ACCEPTED", currentUser.require().getId(), request);
        return new MessageResponse("If email delivery is configured, a verification link has been sent.");
    }

    @PostMapping("/verify")
    public UserResponse verify(@Valid @RequestBody TokenRequest request, HttpServletRequest servletRequest) {
        AppUser user = lifecycle.verifyEmail(request.token());
        audit.record("EMAIL_VERIFIED", "SUCCEEDED", user.getId(), servletRequest);
        return response(user);
    }

    @PostMapping("/password/forgot")
    public MessageResponse forgotPassword(@Valid @RequestBody PasswordResetRequest request,
                                          HttpServletRequest servletRequest) {
        lifecycle.requestPasswordReset(request.email());
        audit.record("PASSWORD_RESET_REQUESTED", "ACCEPTED", null, servletRequest);
        return new MessageResponse("If the account exists, password reset instructions have been sent.");
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody NewPasswordRequest request,
                                         HttpServletRequest servletRequest) {
        lifecycle.resetPassword(request.token(), request.newPassword());
        audit.record("PASSWORD_RESET", "SUCCEEDED", null, servletRequest);
        return new MessageResponse("Password updated. Sign in again on your devices.");
    }

    @PostMapping("/recovery/reset")
    public MessageResponse recover(@Valid @RequestBody RecoveryResetRequest request,
                                   HttpServletRequest servletRequest) {
        lifecycle.recoverWithCode(request.email(), request.recoveryCode(), request.newPassword());
        audit.record("ACCOUNT_RECOVERY", "SUCCEEDED", null, servletRequest);
        return new MessageResponse("Password updated. The recovery code has been consumed.");
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

    private UserResponse response(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isEmailVerified(),
            lifecycle.emailDeliveryConfigured(), user.getAccountRole());
    }
}
