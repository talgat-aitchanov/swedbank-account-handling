package ee.swedbank.service;

import ee.swedbank.domain.SupportedCurrency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class ExchangeRateService {

    /**
     * Exchange rates expressed as units of each currency per 1 EUR.
     * E.g. EUR_RATES[USD] = 1.10 means 1 EUR = 1.10 USD.
     */
    private static final Map<SupportedCurrency, BigDecimal> EUR_RATES = Map.of(
            SupportedCurrency.EUR, BigDecimal.ONE,
            SupportedCurrency.USD, new BigDecimal("1.10"),
            SupportedCurrency.SEK, new BigDecimal("11.20"),
            SupportedCurrency.GBP, new BigDecimal("0.86")
    );

    /**
     * Converts {@code amount} from {@code from} to {@code to} currency,
     * pivoting through EUR.
     */
    public BigDecimal convert(BigDecimal amount, SupportedCurrency from, SupportedCurrency to) {
        if (from == to) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        // amount / EUR_RATES[from] = amount in EUR; then * EUR_RATES[to] = amount in target
        BigDecimal amountInEur = amount.divide(EUR_RATES.get(from), 10, RoundingMode.HALF_UP);
        return amountInEur.multiply(EUR_RATES.get(to)).setScale(2, RoundingMode.HALF_UP);
    }
}
