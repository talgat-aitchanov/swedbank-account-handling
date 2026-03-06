package ee.swedbank.service;

import ee.swedbank.domain.SupportedCurrency;
import lombok.Getter;

@Getter
public class UnsupportedCurrencyException extends RuntimeException {
    private final Long accountId;
    private final SupportedCurrency currency;

    public UnsupportedCurrencyException(Long accountId, SupportedCurrency currency) {
        super("Account does not have a balance for currency " + currency);
        this.accountId = accountId;
        this.currency = currency;
    }
}
