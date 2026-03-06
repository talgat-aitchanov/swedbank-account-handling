package ee.swedbank.service;

import ee.swedbank.domain.SupportedCurrency;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InsufficientFundsException extends RuntimeException {
    private final Long accountId;
    private final SupportedCurrency currency;
    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientFundsException(Long accountId, SupportedCurrency currency,
                                      BigDecimal available, BigDecimal requested) {
        super("Insufficient funds on account " + accountId + ": cannot process " + requested
                + " " + currency + ", available balance is " + available + ".");
        this.accountId = accountId;
        this.currency = currency;
        this.available = available;
        this.requested = requested;
    }
}
