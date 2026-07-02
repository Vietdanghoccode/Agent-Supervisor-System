package com.viettel.conversation.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency-Key was already used with a different request payload");
    }

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
