package ee.swedbank.service;

public class AccountAccessDeniedException extends RuntimeException {

    public AccountAccessDeniedException(Long accountId) {
        super("Access denied to account " + accountId + ".");
    }
}

