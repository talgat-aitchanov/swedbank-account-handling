package ee.swedbank.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.swedbank.api.dto.*;
import ee.swedbank.domain.SupportedCurrency;
import ee.swedbank.service.ExternalLoggingClient;
import ee.swedbank.service.ExternalLoggingFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountControllerIntegrationTest {

    private static final String BASE_URL = "/api/v1/accounts";
    private static final String ADMIN_URL = "/api/v1/admin/accounts";
    private static final String LOGIN_URL = "/auth/login";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ExternalLoggingClient externalLoggingClient;
    private String userToken;
    private String adminToken;
    /**
     * Fresh account created for "user" in @BeforeEach — used as subject for all account tests.
     */
    private Long accountId;

    // ── helpers ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        userToken = loginAndGetToken("user", "password");
        adminToken = loginAndGetToken("admin", "admin123");
        accountId = createFreshAccount();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private Long createFreshAccount() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accountId").asLong();
    }

    private void deposit(Long id, BigDecimal amount, SupportedCurrency currency) throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyRequest(amount, currency))))
                .andExpect(status().isOk());
    }

    // ── auth tests ─────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void request_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void request_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").isString());
    }

    // ── account owner tests ────────────────────────────────────────────────────

    @Test
    void createAccount() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber());
    }

    @Test
    void getMyAccounts_returnsOwnedAccountsWithBalances() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.accountId == " + accountId + ")]").exists())
                .andExpect(jsonPath("$[?(@.accountId == " + accountId + ")].ownerUsername").value("user"))
                .andExpect(jsonPath("$[?(@.accountId == " + accountId + ")].balances.EUR").value(0.00));
    }

    @Test
    void getBalancesReturnsAllCurrenciesAsZero() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + accountId + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balances.EUR").value(0.00))
                .andExpect(jsonPath("$.balances.USD").value(0.00))
                .andExpect(jsonPath("$.balances.SEK").value(0.00))
                .andExpect(jsonPath("$.balances.GBP").value(0.00));
    }

    @Test
    void accessOtherUsersAccount_returns403() throws Exception {
        // admin account has id != accountId (created by "user"), so accessing it as "user" must fail
        // Use a fake id that doesn't belong to user
        Long otherId = accountId + 9999L;
        mockMvc.perform(get(BASE_URL + "/" + otherId + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isNotFound()); // account doesn't exist → 404
    }

    @Test
    void deposit_addsMoneyToAccount() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("100.50"), SupportedCurrency.EUR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balances.EUR").value(100.50));
    }

    @Test
    void withdraw_debitsMoneyFromAccount() throws Exception {
        willDoNothing().given(externalLoggingClient).logWithdrawal();
        deposit(accountId, new BigDecimal("200.00"), SupportedCurrency.EUR);

        mockMvc.perform(post(BASE_URL + "/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balances.EUR").value(150.00));
    }

    @Test
    void withdraw_failsOnInsufficientFunds() throws Exception {
        willDoNothing().given(externalLoggingClient).logWithdrawal();
        deposit(accountId, new BigDecimal("10.00"), SupportedCurrency.EUR);

        mockMvc.perform(post(BASE_URL + "/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("EUR")))
                .andExpect(jsonPath("$.detail").value(containsString("50")));
    }

    @Test
    void withdraw_failsWhenExternalCallFails() throws Exception {
        willThrow(new ExternalLoggingFailedException("external fail")).given(externalLoggingClient).logWithdrawal();
        deposit(accountId, new BigDecimal("200.00"), SupportedCurrency.EUR);

        mockMvc.perform(post(BASE_URL + "/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The request could not be processed due to an external dependency failure."));
    }

    @Test
    void exchange_convertsMoneyBetweenCurrencies() throws Exception {
        deposit(accountId, new BigDecimal("100.00"), SupportedCurrency.EUR);

        mockMvc.perform(post(BASE_URL + "/" + accountId + "/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExchangeRequest(new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.USD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balances.EUR").value(50.00))
                .andExpect(jsonPath("$.balances.USD").value(55.00));
    }

    @Test
    void exchange_failsWhenSameCurrency() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExchangeRequest(new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.EUR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("EUR")));
    }

    @Test
    void validationError_missingAmount() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"EUR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("amount")))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void validationError_negativeAmount() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("-5.00"), SupportedCurrency.EUR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void accountNotFound_returns404() throws Exception {
        mockMvc.perform(get(ADMIN_URL + "/999999/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("999999")));
    }

    @Test
    void idempotency_depositReturnsSameResponseOnReplay() throws Exception {
        String body = objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("100.00"), SupportedCurrency.EUR));

        MvcResult first = mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "dep-idem"))
                .andExpect(status().isOk()).andReturn();

        MvcResult second = mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "dep-idem"))
                .andExpect(status().isOk()).andReturn();

        then(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        mockMvc.perform(get(BASE_URL + "/" + accountId + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(jsonPath("$.balances.EUR").value(100.00));
    }

    @Test
    void idempotency_withdrawReturnsSameResponseOnReplay() throws Exception {
        willDoNothing().given(externalLoggingClient).logWithdrawal();
        deposit(accountId, new BigDecimal("200.00"), SupportedCurrency.EUR);
        String body = objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR));

        MvcResult first = mockMvc.perform(post(BASE_URL + "/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "wd-idem"))
                .andExpect(status().isOk()).andReturn();

        MvcResult second = mockMvc.perform(post(BASE_URL + "/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "wd-idem"))
                .andExpect(status().isOk()).andReturn();

        then(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        mockMvc.perform(get(BASE_URL + "/" + accountId + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(jsonPath("$.balances.EUR").value(150.00));
    }

    @Test
    void idempotency_exchangeReturnsSameResponseOnReplay() throws Exception {
        deposit(accountId, new BigDecimal("100.00"), SupportedCurrency.EUR);
        String body = objectMapper.writeValueAsString(
                new ExchangeRequest(new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.USD));

        MvcResult first = mockMvc.perform(post(BASE_URL + "/" + accountId + "/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "ex-idem"))
                .andExpect(status().isOk()).andReturn();

        MvcResult second = mockMvc.perform(post(BASE_URL + "/" + accountId + "/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Idempotency-Key", "ex-idem"))
                .andExpect(status().isOk()).andReturn();

        then(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        mockMvc.perform(get(BASE_URL + "/" + accountId + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(jsonPath("$.balances.EUR").value(50.00))
                .andExpect(jsonPath("$.balances.USD").value(55.00));
    }

    // ── bad-input / protocol error tests ──────────────────────────────────────

    @Test
    void malformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be parsed")));
    }

    @Test
    void invalidCurrencyEnumInBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"currency\": \"XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("XYZ")))
                .andExpect(jsonPath("$.detail").value(containsString("EUR")));
    }

    @Test
    void nonNumericAccountId_returnsBadRequest() throws Exception {
        mockMvc.perform(get(BASE_URL + "/not-a-number/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not-a-number")));
    }

    @Test
    void wrongHttpMethod_returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void missingContentType_returnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .content(objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("100.00"), SupportedCurrency.EUR))))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ── admin tests ────────────────────────────────────────────────────────────

    @Test
    void adminEndpoint_withAdminRole_returnsAllAccountsGroupedByUser() throws Exception {
        mockMvc.perform(get(ADMIN_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.username == 'user')]").exists())
                .andExpect(jsonPath("$[?(@.username == 'user')].accounts").isArray());
    }

    @Test
    void adminEndpoint_filterByUsername_returnsOnlyThatUsersAccounts() throws Exception {
        mockMvc.perform(get(ADMIN_URL + "?username=user")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").value("user"))
                .andExpect(jsonPath("$[0].accounts").isArray());
    }

    @Test
    void adminEndpoint_canViewAnyAccountBalances() throws Exception {
        mockMvc.perform(get(ADMIN_URL + "/" + accountId + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId));
    }

    @Test
    void adminEndpoint_deposit_creditsAccountAndRecordsNote() throws Exception {
        mockMvc.perform(post(ADMIN_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminMoneyRequest(new BigDecimal("250.00"), SupportedCurrency.EUR, "Bonus credit"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balances.EUR").value(250.00));
    }

    @Test
    void adminEndpoint_withdraw_debitsAccount() throws Exception {
        willDoNothing().given(externalLoggingClient).logWithdrawal();
        deposit(accountId, new BigDecimal("300.00"), SupportedCurrency.EUR);

        mockMvc.perform(post(ADMIN_URL + "/" + accountId + "/withdraw")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminMoneyRequest(new BigDecimal("100.00"), SupportedCurrency.EUR, "Fee correction"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.EUR").value(200.00));
    }

    @Test
    void adminEndpoint_exchange_convertsBalance() throws Exception {
        deposit(accountId, new BigDecimal("100.00"), SupportedCurrency.EUR);

        mockMvc.perform(post(ADMIN_URL + "/" + accountId + "/exchange")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AdminExchangeRequest(new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.USD, "Admin exchange"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.EUR").value(50.00))
                .andExpect(jsonPath("$.balances.USD").value(55.00));
    }

    @Test
    void adminEndpoint_withUserRole_returns403() throws Exception {
        mockMvc.perform(get(ADMIN_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied."));
    }

    @Test
    void adminEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(ADMIN_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").isString());
    }

    // ── role-separation tests ──────────────────────────────────────────────────

    @Test
    void accountEndpoint_withAdminRole_returns403() throws Exception {
        // ADMIN must NOT be able to call user account endpoints
        mockMvc.perform(get(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied."));
    }

    @Test
    void accountEndpoint_createAccount_withAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied."));
    }

    @Test
    void accountEndpoint_deposit_withAdminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MoneyRequest(new BigDecimal("100.00"), SupportedCurrency.EUR))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied."));
    }

    @Test
    void accountEndpoint_balances_withAdminRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + accountId + "/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied."));
    }
}

