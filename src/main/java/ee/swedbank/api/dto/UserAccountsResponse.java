package ee.swedbank.api.dto;

import java.util.List;

/**
 * Admin aggregated view: one user with all their accounts and balances.
 */
public record UserAccountsResponse(
        String username,
        List<AccountWithBalancesResponse> accounts
) {
}

