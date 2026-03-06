package ee.swedbank.service;

import ee.swedbank.domain.SupportedCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.BDDAssertions.then;

class ExchangeRateServiceTest {

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateService();
    }

    @Test
    void convert_sameCurrency_returnsSameAmount() {
        // given
        BigDecimal amount = new BigDecimal("100.00");

        // when
        BigDecimal result = exchangeRateService.convert(amount, SupportedCurrency.EUR, SupportedCurrency.EUR);

        // then
        then(result).isEqualByComparingTo("100.00");
    }

    @Test
    void convert_eurToUsd_appliesRateCorrectly() {
        // given
        BigDecimal amount = new BigDecimal("100.00");

        // when
        BigDecimal result = exchangeRateService.convert(amount, SupportedCurrency.EUR, SupportedCurrency.USD);

        // then
        then(result).isEqualByComparingTo("110.00");
    }

    @Test
    void convert_usdToEur_appliesInverseRateCorrectly() {
        // given
        BigDecimal amount = new BigDecimal("110.00");

        // when
        BigDecimal result = exchangeRateService.convert(amount, SupportedCurrency.USD, SupportedCurrency.EUR);

        // then
        then(result).isEqualByComparingTo("100.00");
    }

    @Test
    void convert_eurToSek_appliesRateCorrectly() {
        // given
        BigDecimal amount = new BigDecimal("1.00");

        // when
        BigDecimal result = exchangeRateService.convert(amount, SupportedCurrency.EUR, SupportedCurrency.SEK);

        // then
        then(result).isEqualByComparingTo("11.20");
    }

    @Test
    void convert_eurToGbp_appliesRateCorrectly() {
        // given
        BigDecimal amount = new BigDecimal("100.00");

        // when
        BigDecimal result = exchangeRateService.convert(amount, SupportedCurrency.EUR, SupportedCurrency.GBP);

        // then
        then(result).isEqualByComparingTo("86.00");
    }

    @Test
    void convert_gbpToUsd_pivotsThroughEur() {
        // given  — 100 GBP → EUR: 100/0.86 ≈ 116.279... → USD: *1.10 ≈ 127.91
        BigDecimal amount = new BigDecimal("100.00");

        // when
        BigDecimal result = exchangeRateService.convert(amount, SupportedCurrency.GBP, SupportedCurrency.USD);

        // then
        then(result).isEqualByComparingTo("127.91");
    }

    @Test
    void convert_smallAmount_roundsHalfUp() {
        // given
        BigDecimal amount = new BigDecimal("0.01");

        // when
        BigDecimal result = exchangeRateService.convert(amount, SupportedCurrency.EUR, SupportedCurrency.USD);

        // then
        then(result).isEqualByComparingTo("0.01");
    }
}
