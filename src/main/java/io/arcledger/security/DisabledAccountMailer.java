package io.arcledger.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "arcledger.mail.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAccountMailer implements AccountMailer {
    @Override public boolean isConfigured() { return false; }
    @Override public void sendVerification(String recipient, String token) {}
    @Override public void sendPasswordReset(String recipient, String token) {}
    @Override public void sendStoryInvitation(String recipient, String inviterName, String storyTitle, String token) {}
}
