package io.arcledger.security;

import io.arcledger.domain.*;
import io.arcledger.repository.*;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

import java.time.*;
import java.util.*;

@Service
public class AccountLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(AccountLifecycleService.class);
    private static final int RECOVERY_CODE_COUNT = 8;

    private final AppUserRepository users;
    private final AccountTokenRepository tokens;
    private final AccountRecoveryCodeRepository recoveryCodes;
    private final CurrentUserService currentUser;
    private final SecureTokenService secureTokens;
    private final PasswordEncoder passwords;
    private final AccountMailer mailer;
    private final JdbcTemplate jdbc;

    public AccountLifecycleService(AppUserRepository users, AccountTokenRepository tokens,
        AccountRecoveryCodeRepository recoveryCodes, CurrentUserService currentUser,
        SecureTokenService secureTokens, PasswordEncoder passwords, AccountMailer mailer, JdbcTemplate jdbc) {
        this.users = users;
        this.tokens = tokens;
        this.recoveryCodes = recoveryCodes;
        this.currentUser = currentUser;
        this.secureTokens = secureTokens;
        this.passwords = passwords;
        this.mailer = mailer;
        this.jdbc = jdbc;
    }

    public boolean emailDeliveryConfigured() { return mailer.isConfigured(); }

    @Transactional
    public void beginVerification(AppUser user) {
        if (user.isEmailVerified()) return;
        tokens.deleteByUserIdAndPurpose(user.getId(), AccountTokenPurpose.EMAIL_VERIFICATION);
        String rawToken = secureTokens.generate();
        tokens.save(new AccountToken(user, AccountTokenPurpose.EMAIL_VERIFICATION,
            secureTokens.hash(rawToken), Instant.now().plus(Duration.ofHours(24))));
        sendAfterCommit(() -> mailer.sendVerification(user.getEmail(), rawToken));
    }

    @Transactional
    public void requestVerification() { beginVerification(currentUser.require()); }

    @Transactional
    public AppUser verifyEmail(String rawToken) {
        AccountToken token = usable(rawToken, AccountTokenPurpose.EMAIL_VERIFICATION);
        Instant now = Instant.now();
        token.getUser().verifyEmail(now);
        token.consume(now);
        return token.getUser();
    }

    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(AppUserDetailsService.normalize(email)).filter(AppUser::isEnabled).ifPresent(user -> {
            tokens.deleteByUserIdAndPurpose(user.getId(), AccountTokenPurpose.PASSWORD_RESET);
            String rawToken = secureTokens.generate();
            tokens.save(new AccountToken(user, AccountTokenPurpose.PASSWORD_RESET,
                secureTokens.hash(rawToken), Instant.now().plus(Duration.ofMinutes(30))));
            sendAfterCommit(() -> mailer.sendPasswordReset(user.getEmail(), rawToken));
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        AccountToken token = usable(rawToken, AccountTokenPurpose.PASSWORD_RESET);
        Instant now = Instant.now();
        token.getUser().changePassword(passwords.encode(newPassword), now);
        token.consume(now);
        revokeAllSessions(token.getUser());
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        AppUser user = currentUser.require();
        if (!passwords.matches(currentPassword, user.getPasswordHash())) throw new PasswordConfirmationException();
        user.changePassword(passwords.encode(newPassword), Instant.now());
        revokeAllSessions(user);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(String currentPassword) {
        AppUser user = currentUser.require();
        if (!passwords.matches(currentPassword, user.getPasswordHash())) throw new PasswordConfirmationException();
        recoveryCodes.deleteByUserId(user.getId());
        List<String> rawCodes = new ArrayList<>();
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            String rawCode = formatRecoveryCode(secureTokens.generate());
            recoveryCodes.save(new AccountRecoveryCode(user, secureTokens.hash(normalizeRecoveryCode(rawCode))));
            rawCodes.add(rawCode);
        }
        return List.copyOf(rawCodes);
    }

    @Transactional
    public void recoverWithCode(String email, String rawCode, String newPassword) {
        AppUser user = users.findByEmail(AppUserDetailsService.normalize(email))
            .orElseThrow(InvalidAccountTokenException::new);
        AccountRecoveryCode code = recoveryCodes.findByUserIdAndCodeHash(
                user.getId(), secureTokens.hash(normalizeRecoveryCode(rawCode)))
            .filter(AccountRecoveryCode::isUsable)
            .orElseThrow(InvalidAccountTokenException::new);
        code.consume(Instant.now());
        user.changePassword(passwords.encode(newPassword), Instant.now());
        revokeAllSessions(user);
    }

    private AccountToken usable(String rawToken, AccountTokenPurpose purpose) {
        return tokens.findByTokenHashAndPurpose(secureTokens.hash(rawToken), purpose)
            .filter(token -> token.isUsableAt(Instant.now()))
            .orElseThrow(InvalidAccountTokenException::new);
    }

    private void revokeAllSessions(AppUser user) {
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", user.getEmail());
    }

    private void safelySend(Runnable delivery) {
        if (!mailer.isConfigured()) return;
        try {
            delivery.run();
        } catch (RuntimeException exception) {
            log.warn("Account email delivery failed exception_type={}", exception.getClass().getName());
        }
    }

    private void sendAfterCommit(Runnable delivery) {
        if (!mailer.isConfigured()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safelySend(delivery);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { safelySend(delivery); }
        });
    }

    private static String formatRecoveryCode(String rawToken) {
        String compact = rawToken.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
        return compact.substring(0, 6) + "-" + compact.substring(6, 12) + "-" + compact.substring(12, 18);
    }

    private static String normalizeRecoveryCode(String rawCode) {
        return rawCode == null ? "" : rawCode.replace("-", "").strip().toUpperCase(Locale.ROOT);
    }
}
