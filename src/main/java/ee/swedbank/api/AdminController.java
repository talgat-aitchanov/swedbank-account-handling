package ee.swedbank.api;

import ee.swedbank.api.dto.AdminExchangeRequest;
import ee.swedbank.api.dto.AdminMoneyRequest;
import ee.swedbank.api.dto.BalanceResponse;
import ee.swedbank.api.dto.UserAccountsResponse;
import ee.swedbank.security.AppUserPrincipal;
import ee.swedbank.security.CurrentUser;
import ee.swedbank.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints.
 * Restricted to the ADMIN role; admins can inspect and operate on any account.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AccountService accountService;

    /**
     * List all accounts in the system grouped by owner, with full balances.
     * Optionally filter by {@code username} query parameter.
     */
    @GetMapping
    public ResponseEntity<List<UserAccountsResponse>> getAccounts(
            @RequestParam(required = false) String username) {
        return ResponseEntity.ok(accountService.getUserAccountsWithBalances(username));
    }

    /**
     * Get balances for any single account by its ID.
     */
    @GetMapping("/{accountId}/balances")
    public ResponseEntity<BalanceResponse> getBalances(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getBalance(accountId));
    }

    /**
     * Admin-initiated deposit. Note is recorded in the audit log.
     */
    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<BalanceResponse> deposit(
            @CurrentUser AppUserPrincipal admin,
            @PathVariable Long accountId,
            @Valid @RequestBody AdminMoneyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(accountService.addMoney(
                accountId, request.amount(), request.currency(),
                idempotencyKey, admin.username(), request.note()));
    }

    /**
     * Admin-initiated withdrawal. Note is recorded in the audit log.
     */
    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<BalanceResponse> withdraw(
            @CurrentUser AppUserPrincipal admin,
            @PathVariable Long accountId,
            @Valid @RequestBody AdminMoneyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(accountService.withdrawMoney(
                accountId, request.amount(), request.currency(),
                idempotencyKey, admin.username(), request.note()));
    }

    /**
     * Admin-initiated currency exchange. Note is recorded in the audit log.
     */
    @PostMapping("/{accountId}/exchange")
    public ResponseEntity<BalanceResponse> exchange(
            @CurrentUser AppUserPrincipal admin,
            @PathVariable Long accountId,
            @Valid @RequestBody AdminExchangeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(accountService.exchange(
                accountId, request.amount(), request.fromCurrency(), request.toCurrency(),
                idempotencyKey, admin.username(), request.note()));
    }
}
