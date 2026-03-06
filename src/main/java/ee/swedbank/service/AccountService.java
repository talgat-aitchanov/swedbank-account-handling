package ee.swedbank.service;

import ee.swedbank.api.dto.AccountWithBalancesResponse;
import ee.swedbank.api.dto.BalanceResponse;
import ee.swedbank.api.dto.BalanceResponseMapper;
import ee.swedbank.api.dto.UserAccountsResponse;
import ee.swedbank.domain.Account;
import ee.swedbank.domain.AccountBalance;
import ee.swedbank.domain.SupportedCurrency;
import ee.swedbank.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final ExchangeRateService exchangeRateService;
    private final ExternalLoggingClient externalLoggingClient;
    private final IdempotencyService idempotencyService;
    private final BalanceResponseMapper balanceResponseMapper;

    // ── account management ────────────────────────────────────────────────────

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Long createAccount(String ownerUsername) {
        Account account = new Account();
        account.setOwnerUsername(ownerUsername);
        return accountRepository.save(account).getId();
    }

    /**
     * All accounts with full balances for the calling user.
     */
    @Transactional(readOnly = true)
    public List<AccountWithBalancesResponse> getAccountsWithBalances(String username) {
        return accountRepository.findAllWithBalancesByOwnerUsername(username).stream()
                .map(balanceResponseMapper::toDetailedResponse)
                .toList();
    }

    /**
     * Admin: accounts grouped by user.
     * If {@code username} is provided, returns only that user's accounts.
     * Otherwise returns every account in the system grouped by owner.
     */
    @Transactional(readOnly = true)
    public List<UserAccountsResponse> getUserAccountsWithBalances(String username) {
        List<Account> accounts = username != null && !username.isBlank()
                ? accountRepository.findAllWithBalancesByOwnerUsername(username)
                : accountRepository.findAllWithBalances();

        Map<String, List<AccountWithBalancesResponse>> grouped = new LinkedHashMap<>();
        for (Account a : accounts) {
            grouped.computeIfAbsent(a.getOwnerUsername(), k -> new java.util.ArrayList<>())
                    .add(balanceResponseMapper.toDetailedResponse(a));
        }
        return grouped.entrySet().stream()
                .map(e -> new UserAccountsResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        return balanceResponseMapper.toResponse(findAccountWithBalances(accountId));
    }

    // ── mutating operations ───────────────────────────────────────────────────

    /**
     * Verifies the given username owns the given account.
     * Throws {@link AccountAccessDeniedException} (→ 403) if not.
     */
    @Transactional(readOnly = true)
    public void verifyOwnership(String username, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (!username.equals(account.getOwnerUsername())) {
            throw new AccountAccessDeniedException(accountId);
        }
    }

    @Transactional
    public BalanceResponse addMoney(Long accountId, BigDecimal amount, SupportedCurrency currency,
                                    String idempotencyKey, String initiatedBy, String note) {
        return idempotencyService.executeIdempotent(idempotencyKey, OperationType.DEPOSIT,
                initiatedBy, note, () -> {
                    Account account = findAccountWithBalances(accountId);
                    AccountBalance balance = findOrCreateBalance(account, currency);
                    balance.setAmount(scaled(balance.getAmount().add(amount)));
                    accountRepository.save(account);
                    return balanceResponseMapper.toResponse(account);
                });
    }

    @Transactional
    public BalanceResponse withdrawMoney(Long accountId, BigDecimal amount, SupportedCurrency currency,
                                         String idempotencyKey, String initiatedBy, String note) {
        return idempotencyService.executeIdempotent(idempotencyKey, OperationType.WITHDRAW,
                initiatedBy, note, () -> {
                    Account account = findAccountWithBalances(accountId);
                    AccountBalance balance = requireBalance(account, accountId, currency);

                    if (balance.getAmount().compareTo(amount) < 0) {
                        throw new InsufficientFundsException(accountId, currency, balance.getAmount(), amount);
                    }

                    externalLoggingClient.logWithdrawal();
                    balance.setAmount(scaled(balance.getAmount().subtract(amount)));
                    accountRepository.save(account);
                    return balanceResponseMapper.toResponse(account);
                });
    }

    // ── private helpers ───────────────────────────────────────────────────────

    @Transactional
    public BalanceResponse exchange(Long accountId, BigDecimal amount,
                                    SupportedCurrency fromCurrency, SupportedCurrency toCurrency,
                                    String idempotencyKey, String initiatedBy, String note) {
        if (fromCurrency == toCurrency) {
            throw new InvalidRequestException(
                    "Cannot exchange on account " + accountId
                            + ": fromCurrency and toCurrency must differ, but both are " + fromCurrency + ".");
        }
        return idempotencyService.executeIdempotent(idempotencyKey, OperationType.EXCHANGE,
                initiatedBy, note, () -> {
                    Account account = findAccountWithBalances(accountId);
                    AccountBalance source = requireBalance(account, accountId, fromCurrency);

                    if (source.getAmount().compareTo(amount) < 0) {
                        throw new InsufficientFundsException(accountId, fromCurrency, source.getAmount(), amount);
                    }

                    source.setAmount(scaled(source.getAmount().subtract(amount)));
                    BigDecimal converted = exchangeRateService.convert(amount, fromCurrency, toCurrency);
                    AccountBalance target = findOrCreateBalance(account, toCurrency);
                    target.setAmount(scaled(target.getAmount().add(converted)));

                    accountRepository.save(account);
                    return balanceResponseMapper.toResponse(account);
                });
    }

    private Account findAccountWithBalances(Long accountId) {
        return accountRepository.findWithBalancesById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private AccountBalance requireBalance(Account account, Long accountId, SupportedCurrency currency) {
        return account.getBalances().stream()
                .filter(b -> b.getCurrency() == currency)
                .findFirst()
                .orElseThrow(() -> new UnsupportedCurrencyException(accountId, currency));
    }

    private AccountBalance findOrCreateBalance(Account account, SupportedCurrency currency) {
        return account.getBalances().stream()
                .filter(b -> b.getCurrency() == currency)
                .findFirst()
                .orElseGet(() -> {
                    AccountBalance nb = new AccountBalance(account, currency,
                            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    account.getBalances().add(nb);
                    return nb;
                });
    }
}
