package ee.swedbank.service;

import ee.swedbank.domain.SupportedCurrency;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class ExchangeRateService {

    private static final int CURRENCY_SCALE = 2;
    private static final int INTERMEDIATE_SCALE = 12;

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
            return amount.setScale(CURRENCY_SCALE, RoundingMode.DOWN);
        }
        BigDecimal amountInEur = amount.divide(EUR_RATES.get(from), INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal targetRaw = amountInEur.multiply(EUR_RATES.get(to));
        return targetRaw.setScale(CURRENCY_SCALE, RoundingMode.DOWN);
    }
}
