package ee.swedbank.service;

/**
 * Thrown when request data violates a business rule (distinct from bean validation),
 * e.g. fromCurrency == toCurrency in an exchange request.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
