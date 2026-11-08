package io.github.hyunjyyyy.heavyeqtracker.location;

public class DuplicateInProgressException extends RuntimeException {
    public DuplicateInProgressException(String idempotencyKey) {
        super("request with Idempotency-Key " + idempotencyKey + " is already being processed");
    }
}
