package io.arcledger.security;

public interface AccountMailer {
    boolean isConfigured();
    void sendVerification(String recipient, String token);
    void sendPasswordReset(String recipient, String token);
    void sendStoryInvitation(String recipient, String inviterName, String storyTitle, String token);
}
