package io.arcledger.api;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException() {
        super("The idempotency key was already used for a different scene request.");
    }
}
