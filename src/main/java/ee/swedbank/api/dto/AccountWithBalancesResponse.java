package ee.swedbank.api.dto;

import ee.swedbank.domain.SupportedCurrency;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Account with its balances — used in both the user's own view and admin's aggregated view.
 */
public record AccountWithBalancesResponse(
        Long accountId,
        String ownerUsername,
        Map<SupportedCurrency, BigDecimal> balances
) {
}

