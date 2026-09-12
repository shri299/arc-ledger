package io.arcledger.api;

import io.arcledger.api.ApiModels.ErrorResponse;
import io.arcledger.security.EmailAlreadyRegisteredException;
import io.arcledger.security.InvalidAccountTokenException;
import io.arcledger.security.PasswordConfirmationException;
import io.arcledger.security.CollaborationConflictException;
import io.arcledger.security.EmailVerificationRequiredException;
import io.arcledger.security.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final SecurityAuditService audit;

    public ApiExceptionHandler(SecurityAuditService audit) {
        this.audit = audit;
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> notFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> routeNotFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested endpoint was not found.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
        ConstraintViolationException.class, HttpMessageNotReadableException.class, ServletRequestBindingException.class,
        MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> badRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request validation failed.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> dataConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data.");
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> conflict(EmailAlreadyRegisteredException exception, HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", exception.getMessage());
    }

    @ExceptionHandler(CollaborationConflictException.class)
    public ResponseEntity<ErrorResponse> collaborationConflict(CollaborationConflictException exception,
                                                                HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "COLLABORATION_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ErrorResponse> idempotencyConflict(IdempotencyKeyConflictException exception,
                                                              HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage());
    }

    @ExceptionHandler({InvalidAccountTokenException.class, PasswordConfirmationException.class})
    public ResponseEntity<ErrorResponse> accountAction(RuntimeException exception, HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "ACCOUNT_ACTION_REJECTED", exception.getMessage());
    }

    @ExceptionHandler(EmailVerificationRequiredException.class)
    public ResponseEntity<ErrorResponse> emailVerificationRequired(EmailVerificationRequiredException exception,
                                                                    HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> unauthorized(HttpServletRequest request) {
        return response(request, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> forbidden(AccessDeniedException exception, HttpServletRequest request) {
        audit.record("ACCESS_DENIED", "REJECTED", null, request);
        return response(request, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        String requestId = RequestIds.current(request);
        log.error("Unhandled API failure request_id={} exception_type={}", requestId, exception.getClass().getName(), exception);
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred. Use the request ID when contacting support.");
    }

    private ResponseEntity<ErrorResponse> response(HttpServletRequest request, HttpStatus status,
                                                    String code, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(code, message, Instant.now(), RequestIds.current(request)));
    }
}
