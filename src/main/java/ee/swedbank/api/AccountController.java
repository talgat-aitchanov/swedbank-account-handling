package ee.swedbank.api;

import ee.swedbank.api.dto.*;
import ee.swedbank.security.AppUserPrincipal;
import ee.swedbank.security.CurrentUser;
import ee.swedbank.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints for the authenticated account owner.
 * Restricted to the USER role; ownership of each account is additionally
 * enforced per-request in {@link AccountService#verifyOwnership}.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
public class AccountController {

    private final AccountService accountService;

    /**
     * Create a new account linked to the calling user.
     */
    @PostMapping
    public ResponseEntity<CreateAccountResponse> createAccount(@CurrentUser AppUserPrincipal user) {
        Long id = accountService.createAccount(user.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateAccountResponse(id));
    }

    /**
     * List all accounts with full balances for the calling user.
     */
    @GetMapping
    public ResponseEntity<List<AccountWithBalancesResponse>> getMyAccounts(
            @CurrentUser AppUserPrincipal user) {
        return ResponseEntity.ok(accountService.getAccountsWithBalances(user.username()));
    }

    /**
     * Get balances for a specific account. Ownership is verified.
     */
    @GetMapping("/{accountId}/balances")
    public ResponseEntity<BalanceResponse> getBalances(
            @CurrentUser AppUserPrincipal user,
            @PathVariable Long accountId) {
        accountService.verifyOwnership(user.username(), accountId);
        return ResponseEntity.ok(accountService.getBalance(accountId));
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<BalanceResponse> deposit(
            @CurrentUser AppUserPrincipal user,
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        accountService.verifyOwnership(user.username(), accountId);
        return ResponseEntity.ok(accountService.addMoney(
                accountId, request.amount(), request.currency(),
                idempotencyKey, user.username(), null));
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<BalanceResponse> withdraw(
            @CurrentUser AppUserPrincipal user,
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        accountService.verifyOwnership(user.username(), accountId);
        return ResponseEntity.ok(accountService.withdrawMoney(
                accountId, request.amount(), request.currency(),
                idempotencyKey, user.username(), null));
    }

    @PostMapping("/{accountId}/exchange")
    public ResponseEntity<BalanceResponse> exchange(
            @CurrentUser AppUserPrincipal user,
            @PathVariable Long accountId,
            @Valid @RequestBody ExchangeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        accountService.verifyOwnership(user.username(), accountId);
        return ResponseEntity.ok(accountService.exchange(
                accountId, request.amount(), request.fromCurrency(), request.toCurrency(),
                idempotencyKey, user.username(), null));
    }
}
