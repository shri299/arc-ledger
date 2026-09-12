package io.arcledger.security;

public class InvalidAccountTokenException extends RuntimeException {
    public InvalidAccountTokenException() {
        super("The link or recovery code is invalid or has expired.");
    }
}
