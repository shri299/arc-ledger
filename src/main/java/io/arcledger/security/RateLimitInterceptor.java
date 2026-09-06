package io.arcledger.security;

import io.arcledger.api.ApiErrorWriter;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private record Policy(String name, int limit, long windowSeconds, boolean accountScoped) {}

    private final RateLimitService limiter;
    private final ApiErrorWriter errors;
    private final SecurityAuditService audit;
    private final Policy signup;
    private final Policy login;
    private final Policy scene;
    private final Policy question;

    public RateLimitInterceptor(RateLimitService limiter, ApiErrorWriter errors, SecurityAuditService audit,
        @Value("${arcledger.rate-limit.signup.limit:5}") int signupLimit,
        @Value("${arcledger.rate-limit.signup.window-seconds:3600}") long signupWindow,
        @Value("${arcledger.rate-limit.login.limit:10}") int loginLimit,
        @Value("${arcledger.rate-limit.login.window-seconds:60}") long loginWindow,
        @Value("${arcledger.rate-limit.scene.limit:10}") int sceneLimit,
        @Value("${arcledger.rate-limit.scene.window-seconds:300}") long sceneWindow,
        @Value("${arcledger.rate-limit.question.limit:30}") int questionLimit,
        @Value("${arcledger.rate-limit.question.window-seconds:60}") long questionWindow) {
        this.limiter = limiter;
        this.errors = errors;
        this.audit = audit;
        this.signup = policy("signup", signupLimit, signupWindow, false);
        this.login = policy("login", loginLimit, loginWindow, false);
        this.scene = policy("scene", sceneLimit, sceneWindow, true);
        this.question = policy("question", questionLimit, questionWindow, true);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Policy policy = policy(request);
        if (policy == null || policy.limit() <= 0) return true;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String subject = policy.accountScoped() && authentication != null && authentication.isAuthenticated()
            ? authentication.getName() : request.getRemoteAddr();
        RateLimitService.Decision decision = limiter.check(policy.name() + ":" + hash(subject),
            policy.limit(), policy.windowSeconds());
        response.setHeader("RateLimit-Limit", Integer.toString(decision.limit()));
        response.setHeader("RateLimit-Remaining", Integer.toString(decision.remaining()));
        response.setHeader("RateLimit-Reset", Long.toString(decision.retryAfterSeconds()));
        if (decision.allowed()) return true;

        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        audit.record("RATE_LIMIT_" + policy.name().toUpperCase(), "REJECTED",
            audit.actorId(authentication), request);
        errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
            "Too many requests. Try again later.");
        return false;
    }

    private Policy policy(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && "/auth/signup".equals(path)) return signup;
        if ("POST".equals(method) && "/auth/login".equals(path)) return login;
        if ("POST".equals(method) && path.matches("/stories/[^/]+/chapters/[^/]+/scenes")) return scene;
        if ("GET".equals(method) && path.matches("/stories/[^/]+/ask")) return question;
        return null;
    }

    private static Policy policy(String name, int limit, long windowSeconds, boolean accountScoped) {
        if (limit < 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("Rate-limit configuration must use a non-negative limit and positive window.");
        }
        return new Policy(name, limit, windowSeconds, accountScoped);
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "unknown" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
