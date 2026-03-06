package ee.swedbank.api;

import ee.swedbank.api.dto.*;
import ee.swedbank.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<CreateAccountResponse> createAccount() {
        Long id = accountService.createAccount();
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateAccountResponse(id));
    }

    @GetMapping
    public ResponseEntity<AccountListResponse> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @GetMapping("/{accountId}/balances")
    public ResponseEntity<BalanceResponse> getBalances(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getBalance(accountId));
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<BalanceResponse> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BalanceResponse response = accountService.addMoney(
                accountId, request.amount(), request.currency(), idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<BalanceResponse> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BalanceResponse response = accountService.withdrawMoney(
                accountId, request.amount(), request.currency(), idempotencyKey);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/exchange")
    public ResponseEntity<BalanceResponse> exchange(
            @PathVariable Long accountId,
            @Valid @RequestBody ExchangeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BalanceResponse response = accountService.exchange(
                accountId, request.amount(), request.fromCurrency(), request.toCurrency(), idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
