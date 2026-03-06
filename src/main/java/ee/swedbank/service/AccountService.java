package ee.swedbank.service;

import ee.swedbank.api.dto.AccountListResponse;
import ee.swedbank.api.dto.BalanceResponse;
import ee.swedbank.api.dto.BalanceResponseMapper;
import ee.swedbank.domain.Account;
import ee.swedbank.domain.AccountBalance;
import ee.swedbank.domain.SupportedCurrency;
import ee.swedbank.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final ExchangeRateService exchangeRateService;
    private final ExternalLoggingClient externalLoggingClient;
    private final IdempotencyService idempotencyService;
    private final BalanceResponseMapper balanceResponseMapper;

    @Transactional
    public Long createAccount() {
        return accountRepository.save(new Account()).getId();
    }

    @Transactional(readOnly = true)
    public AccountListResponse getAllAccounts() {
        List<Long> ids = accountRepository.findAllIds();
        return new AccountListResponse(ids);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        return balanceResponseMapper.toResponse(findAccountWithBalances(accountId));
    }

    @Transactional
    public BalanceResponse addMoney(Long accountId, BigDecimal amount, SupportedCurrency currency,
                                    String idempotencyKey) {
        return idempotencyService.executeIdempotent(idempotencyKey, OperationType.DEPOSIT, () -> {
            Account account = findAccountWithBalances(accountId);
            AccountBalance balance = findOrCreateBalance(account, currency);
            balance.setAmount(scaled(balance.getAmount().add(amount)));
            accountRepository.save(account);
            return balanceResponseMapper.toResponse(account);
        });
    }

    @Transactional
    public BalanceResponse withdrawMoney(Long accountId, BigDecimal amount, SupportedCurrency currency,
                                         String idempotencyKey) {
        return idempotencyService.executeIdempotent(idempotencyKey, OperationType.WITHDRAW, () -> {
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

    @Transactional
    public BalanceResponse exchange(Long accountId, BigDecimal amount,
                                    SupportedCurrency fromCurrency, SupportedCurrency toCurrency,
                                    String idempotencyKey) {
        if (fromCurrency == toCurrency) {
            throw new InvalidRequestException(
                    "Cannot exchange on account " + accountId
                            + ": fromCurrency and toCurrency must differ, but both are " + fromCurrency + ".");
        }

        return idempotencyService.executeIdempotent(idempotencyKey, OperationType.EXCHANGE, () -> {
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

    // ── private helpers ───────────────────────────────────────────────────────

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
                    AccountBalance nb = new AccountBalance(account, currency, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    account.getBalances().add(nb);
                    return nb;
                });
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}

