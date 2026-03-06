package ee.swedbank.service;

import lombok.Getter;

@Getter
public class AccountNotFoundException extends RuntimeException {
    private final Long accountId;

    public AccountNotFoundException(Long accountId) {
        super("Account " + accountId + " was not found.");
        this.accountId = accountId;
    }
}
