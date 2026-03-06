package ee.swedbank.api.dto;

import ee.swedbank.domain.SupportedCurrency;

import java.math.BigDecimal;
import java.util.Map;

public record BalanceResponse(Long accountId, Map<SupportedCurrency, BigDecimal> balances) {
}

