package ee.swedbank.service;

/**
 * Thrown when an idempotency response cannot be serialized to or deserialized from JSON.
 * Maps to HTTP 500 Internal Server Error.
 */
public class IdempotencySerializationException extends RuntimeException {
    public IdempotencySerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

