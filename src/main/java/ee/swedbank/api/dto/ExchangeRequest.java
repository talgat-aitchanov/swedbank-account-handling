package ee.swedbank.api.dto;

import ee.swedbank.domain.SupportedCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ExchangeRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        @Digits(integer = 18, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "fromCurrency is required")
        SupportedCurrency fromCurrency,

        @NotNull(message = "toCurrency is required")
        SupportedCurrency toCurrency
) {
}
