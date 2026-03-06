package ee.swedbank.api.dto;

import ee.swedbank.domain.SupportedCurrency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Money request for admin-initiated operations — includes an optional audit note.
 */
public record AdminMoneyRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        BigDecimal amount,

        @NotNull(message = "currency is required")
        SupportedCurrency currency,

        @Size(max = 500, message = "note must not exceed 500 characters")
        String note
) {
}

