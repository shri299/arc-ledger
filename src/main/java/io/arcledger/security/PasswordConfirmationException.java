package io.arcledger.security;

public class PasswordConfirmationException extends RuntimeException {
    public PasswordConfirmationException() {
        super("The current password is incorrect.");
    }
}
