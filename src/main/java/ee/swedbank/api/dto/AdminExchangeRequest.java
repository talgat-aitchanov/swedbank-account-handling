package ee.swedbank.api.dto;

import ee.swedbank.domain.SupportedCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Exchange request for admin-initiated operations — includes an optional audit note.
 */
public record AdminExchangeRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        BigDecimal amount,

        @NotNull(message = "fromCurrency is required")
        SupportedCurrency fromCurrency,

        @NotNull(message = "toCurrency is required")
        SupportedCurrency toCurrency,

        @Size(max = 500, message = "note must not exceed 500 characters")
        String note
) {
}

