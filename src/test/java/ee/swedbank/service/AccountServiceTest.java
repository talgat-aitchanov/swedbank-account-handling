package ee.swedbank.service;

import ee.swedbank.api.dto.AccountListResponse;
import ee.swedbank.api.dto.BalanceResponse;
import ee.swedbank.api.dto.BalanceResponseMapper;
import ee.swedbank.domain.Account;
import ee.swedbank.domain.AccountBalance;
import ee.swedbank.domain.SupportedCurrency;
import ee.swedbank.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private ExternalLoggingClient externalLoggingClient;
    @Mock private IdempotencyService idempotencyService;
    @Mock private BalanceResponseMapper balanceResponseMapper;

    @InjectMocks
    private AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account(1L);
    }

    /** Makes executeIdempotent a transparent pass-through for the current test. */
    private void usePassThroughIdempotency() {
        lenient().when(idempotencyService.executeIdempotent(any(), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
    }

    // ── getAllAccounts ─────────────────────────────────────────────────────────

    @Test
    void getAllAccounts_noAccountsExist_returnsEmptyList() {
        // given
        given(accountRepository.findAllIds()).willReturn(List.of());

        // when
        AccountListResponse response = accountService.getAllAccounts();

        // then
        then(response.accountIds()).isEmpty();
    }

    @Test
    void getAllAccounts_accountsExist_returnsAllIds() {
        // given
        given(accountRepository.findAllIds()).willReturn(List.of(1L, 2L, 3L));

        // when
        AccountListResponse response = accountService.getAllAccounts();

        // then
        then(response.accountIds()).containsExactly(1L, 2L, 3L);
    }

    // ── createAccount ─────────────────────────────────────────────────────────

    @Test
    void createAccount_always_persistsAndReturnsGeneratedId() {
        // given
        given(accountRepository.save(any(Account.class))).willReturn(new Account(42L));

        // when
        Long id = accountService.createAccount();

        // then
        then(id).isEqualTo(42L);
        BDDMockito.then(accountRepository).should().save(any(Account.class));
    }

    // ── getBalance ────────────────────────────────────────────────────────────

    @Test
    void getBalance_accountWithNoBalances_delegatesToMapper() {
        // given
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));
        BalanceResponse expected = new BalanceResponse(1L, Map.of());
        given(balanceResponseMapper.toResponse(account)).willReturn(expected);

        // when
        BalanceResponse response = accountService.getBalance(1L);

        // then
        then(response).isSameAs(expected);
    }

    @Test
    void getBalance_accountNotFound_throwsAccountNotFoundException() {
        // given
        given(accountRepository.findWithBalancesById(999L)).willReturn(Optional.empty());

        // when / then
        thenThrownBy(() -> accountService.getBalance(999L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ── addMoney (deposit) ────────────────────────────────────────────────────

    @Test
    void addMoney_noPriorBalance_createsAndCreditsBalance() {
        // given
        usePassThroughIdempotency();
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));
        given(accountRepository.save(any(Account.class))).willReturn(account);
        BalanceResponse expected = new BalanceResponse(1L, Map.of(SupportedCurrency.EUR, new BigDecimal("50.00")));
        given(balanceResponseMapper.toResponse(account)).willReturn(expected);

        // when
        BalanceResponse response = accountService.addMoney(1L, new BigDecimal("50.00"), SupportedCurrency.EUR, null);

        // then
        then(response).isSameAs(expected);
        BDDMockito.then(accountRepository).should().save(account);
    }

    @Test
    void addMoney_existingBalance_addsToCurrentAmount() {
        // given
        usePassThroughIdempotency();
        account.getBalances().add(new AccountBalance(account, SupportedCurrency.EUR, new BigDecimal("100.00")));
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));
        given(accountRepository.save(any(Account.class))).willReturn(account);
        given(balanceResponseMapper.toResponse(account)).willAnswer(inv ->
                new BalanceResponse(1L, Map.of(SupportedCurrency.EUR,
                        account.getBalances().get(0).getAmount())));

        // when
        BalanceResponse response = accountService.addMoney(1L, new BigDecimal("50.00"), SupportedCurrency.EUR, null);

        // then
        then(response.balances().get(SupportedCurrency.EUR)).isEqualByComparingTo("150.00");
    }

    @Test
    void addMoney_duplicateIdempotencyKey_returnsCachedResponseWithoutSideEffect() {
        // given — executeIdempotent returns cached value without calling the supplier
        BalanceResponse cached = new BalanceResponse(1L, Map.of(SupportedCurrency.EUR, new BigDecimal("50.00")));
        given(idempotencyService.executeIdempotent(eq("key1"), eq(OperationType.DEPOSIT), any()))
                .willReturn(cached);

        // when
        BalanceResponse response = accountService.addMoney(1L, new BigDecimal("50.00"), SupportedCurrency.EUR, "key1");

        // then
        then(response).isSameAs(cached);
        BDDMockito.then(accountRepository).should(never()).findWithBalancesById(any());
    }

    // ── withdrawMoney ─────────────────────────────────────────────────────────

    @Test
    void withdrawMoney_sufficientFunds_debitsBalanceAndCallsExternalLogger() {
        // given
        usePassThroughIdempotency();
        account.getBalances().add(new AccountBalance(account, SupportedCurrency.EUR, new BigDecimal("100.00")));
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));
        given(accountRepository.save(any(Account.class))).willReturn(account);
        given(balanceResponseMapper.toResponse(account)).willAnswer(inv ->
                new BalanceResponse(1L, Map.of(SupportedCurrency.EUR,
                        account.getBalances().get(0).getAmount())));
        willDoNothing().given(externalLoggingClient).logWithdrawal();

        // when
        BalanceResponse response = accountService.withdrawMoney(1L, new BigDecimal("30.00"), SupportedCurrency.EUR, null);

        // then
        then(response.balances().get(SupportedCurrency.EUR)).isEqualByComparingTo("70.00");
        BDDMockito.then(externalLoggingClient).should().logWithdrawal();
    }

    @Test
    void withdrawMoney_insufficientFunds_throwsInsufficientFundsException() {
        // given — external call must NOT be reached; validation happens before it
        usePassThroughIdempotency();
        account.getBalances().add(new AccountBalance(account, SupportedCurrency.EUR, new BigDecimal("10.00")));
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));

        // when / then
        thenThrownBy(() -> accountService.withdrawMoney(1L, new BigDecimal("20.00"), SupportedCurrency.EUR, null))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("EUR")
                .hasMessageContaining("20")
                .hasMessageContaining("10");
        BDDMockito.then(externalLoggingClient).should(never()).logWithdrawal();
    }

    @Test
    void withdrawMoney_currencyBalanceMissing_throwsUnsupportedCurrencyException() {
        // given — account has no USD balance; external call must NOT be reached
        usePassThroughIdempotency();
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));

        // when / then
        thenThrownBy(() -> accountService.withdrawMoney(1L, new BigDecimal("10.00"), SupportedCurrency.USD, null))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("USD");
        BDDMockito.then(externalLoggingClient).should(never()).logWithdrawal();
    }

    @Test
    void withdrawMoney_externalCallFails_throwsExternalLoggingFailedException() {
        // given — account and funds are valid; external call fails
        usePassThroughIdempotency();
        account.getBalances().add(new AccountBalance(account, SupportedCurrency.EUR, new BigDecimal("100.00")));
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));
        willThrow(new ExternalLoggingFailedException("fail")).given(externalLoggingClient).logWithdrawal();

        // when / then
        thenThrownBy(() -> accountService.withdrawMoney(1L, new BigDecimal("10.00"), SupportedCurrency.EUR, null))
                .isInstanceOf(ExternalLoggingFailedException.class);
    }

    // ── exchange ──────────────────────────────────────────────────────────────

    @Test
    void exchange_sufficientSourceFunds_subtractsSourceAndCreditsTarget() {
        // given
        usePassThroughIdempotency();
        account.getBalances().add(new AccountBalance(account, SupportedCurrency.EUR, new BigDecimal("100.00")));
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));
        given(accountRepository.save(any(Account.class))).willReturn(account);
        given(exchangeRateService.convert(new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.USD))
                .willReturn(new BigDecimal("55.00"));
        given(balanceResponseMapper.toResponse(account)).willAnswer(inv -> {
            Map<SupportedCurrency, BigDecimal> m = Map.of(
                    SupportedCurrency.EUR, account.getBalances().stream()
                            .filter(b -> b.getCurrency() == SupportedCurrency.EUR)
                            .findFirst().map(AccountBalance::getAmount).orElse(BigDecimal.ZERO),
                    SupportedCurrency.USD, account.getBalances().stream()
                            .filter(b -> b.getCurrency() == SupportedCurrency.USD)
                            .findFirst().map(AccountBalance::getAmount).orElse(BigDecimal.ZERO));
            return new BalanceResponse(1L, m);
        });

        // when
        BalanceResponse response = accountService.exchange(1L, new BigDecimal("50.00"),
                SupportedCurrency.EUR, SupportedCurrency.USD, null);

        // then
        then(response.balances().get(SupportedCurrency.EUR)).isEqualByComparingTo("50.00");
        then(response.balances().get(SupportedCurrency.USD)).isEqualByComparingTo("55.00");
    }

    @Test
    void exchange_insufficientSourceFunds_throwsInsufficientFundsException() {
        // given
        usePassThroughIdempotency();
        account.getBalances().add(new AccountBalance(account, SupportedCurrency.EUR, new BigDecimal("10.00")));
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));

        // when / then
        thenThrownBy(() -> accountService.exchange(1L, new BigDecimal("50.00"),
                        SupportedCurrency.EUR, SupportedCurrency.USD, null))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("EUR")
                .hasMessageContaining("50")
                .hasMessageContaining("10");
    }

    @Test
    void exchange_sourceCurrencyBalanceMissing_throwsUnsupportedCurrencyException() {
        // given — account has no balances at all
        usePassThroughIdempotency();
        given(accountRepository.findWithBalancesById(1L)).willReturn(Optional.of(account));

        // when / then
        thenThrownBy(() -> accountService.exchange(1L, new BigDecimal("50.00"),
                        SupportedCurrency.USD, SupportedCurrency.EUR, null))
                .isInstanceOf(UnsupportedCurrencyException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("USD");
    }

    @Test
    void exchange_sameCurrency_throwsInvalidRequestException() {
        // given — check fires before idempotency/repo lookup; no stubs needed

        // when / then
        thenThrownBy(() -> accountService.exchange(1L, new BigDecimal("50.00"),
                        SupportedCurrency.EUR, SupportedCurrency.EUR, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("EUR");
    }

    @Test
    void exchange_duplicateIdempotencyKey_returnsCachedResponseWithoutSideEffect() {
        // given — executeIdempotent returns cached value without calling the supplier
        BalanceResponse cached = new BalanceResponse(1L, Map.of(
                SupportedCurrency.EUR, new BigDecimal("50.00"),
                SupportedCurrency.USD, new BigDecimal("55.00")));
        given(idempotencyService.executeIdempotent(eq("ex-key"), eq(OperationType.EXCHANGE), any()))
                .willReturn(cached);

        // when
        BalanceResponse response = accountService.exchange(1L, new BigDecimal("50.00"),
                SupportedCurrency.EUR, SupportedCurrency.USD, "ex-key");

        // then
        then(response).isSameAs(cached);
        BDDMockito.then(accountRepository).should(never()).findWithBalancesById(any());
    }
}
