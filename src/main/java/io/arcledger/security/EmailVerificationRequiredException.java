package io.arcledger.security;

public class EmailVerificationRequiredException extends RuntimeException {
    public EmailVerificationRequiredException() {
        super("Verify your email address before making changes.");
    }
}
