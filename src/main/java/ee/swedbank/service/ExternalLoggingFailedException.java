package ee.swedbank.service;

public class ExternalLoggingFailedException extends RuntimeException {
    public ExternalLoggingFailedException(String message) {
        super(message);
    }

    public ExternalLoggingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

