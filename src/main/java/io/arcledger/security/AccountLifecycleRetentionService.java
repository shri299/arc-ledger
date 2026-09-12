package io.arcledger.security;

import io.arcledger.repository.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
public class AccountLifecycleRetentionService {
    private final AccountTokenRepository tokens;
    private final AccountRecoveryCodeRepository recoveryCodes;
    private final AccountSessionMetadataRepository sessionMetadata;
    private final StoryInvitationRepository invitations;

    public AccountLifecycleRetentionService(AccountTokenRepository tokens,
        AccountRecoveryCodeRepository recoveryCodes, AccountSessionMetadataRepository sessionMetadata,
        StoryInvitationRepository invitations) {
        this.tokens = tokens;
        this.recoveryCodes = recoveryCodes;
        this.sessionMetadata = sessionMetadata;
        this.invitations = invitations;
    }

    @Scheduled(cron = "0 20 4 * * *")
    @Transactional
    public void prune() {
        Instant now = Instant.now();
        tokens.deleteByExpiresAtBefore(now);
        invitations.deleteByExpiresAtBefore(now.minus(Duration.ofDays(7)));
        recoveryCodes.deleteByConsumedAtBefore(now.minus(Duration.ofDays(30)));
        sessionMetadata.deleteByLastSeenAtBefore(now.minus(Duration.ofDays(2)));
    }
}
