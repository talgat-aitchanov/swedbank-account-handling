package ee.swedbank.api.dto;

import ee.swedbank.domain.Account;
import ee.swedbank.domain.SupportedCurrency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a domain {@link Account} to a {@link BalanceResponse}.
 * All supported currencies are always present in the response; missing balances default to 0.00.
 */
@Component
public class BalanceResponseMapper {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public BalanceResponse toResponse(Account account) {
        Map<SupportedCurrency, BigDecimal> map = new LinkedHashMap<>();
        for (SupportedCurrency c : SupportedCurrency.values()) {
            map.put(c, ZERO);
        }
        account.getBalances().forEach(b ->
                map.put(b.getCurrency(), b.getAmount().setScale(2, RoundingMode.HALF_UP)));
        return new BalanceResponse(account.getId(), map);
    }
}

