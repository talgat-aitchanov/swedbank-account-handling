package ee.swedbank.service;

/**
 * Identifies the type of mutating operation for idempotency tracking.
 */
public enum OperationType {
    DEPOSIT,
    WITHDRAW,
    EXCHANGE
}

