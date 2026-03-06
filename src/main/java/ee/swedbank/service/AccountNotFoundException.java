package ee.swedbank.service;

import lombok.Getter;

@Getter
public class AccountNotFoundException extends RuntimeException {
    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super("Account " + accountId + " was not found.");
        this.accountId = accountId;
    }

    /**
     * Used when lookup is by username rather than ID.
     */
    public AccountNotFoundException(String message) {
        super(message);
        this.accountId = null;
    }
}
