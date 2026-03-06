package ee.swedbank.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.swedbank.api.dto.ExchangeRequest;
import ee.swedbank.api.dto.MoneyRequest;
import ee.swedbank.domain.SupportedCurrency;
import ee.swedbank.service.ExternalLoggingClient;
import ee.swedbank.service.ExternalLoggingFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.BDDAssertions.then;
import static org.hamcrest.Matchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExternalLoggingClient externalLoggingClient;

    private static final String BASE_URL = "/api/v1/accounts";

    // ── helpers ────────────────────────────────────────────────────────────────

    private Long createAccountAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(post(BASE_URL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accountId").asLong();
    }

    private void deposit(Long accountId, BigDecimal amount, SupportedCurrency currency) throws Exception {
        MoneyRequest req = new MoneyRequest(amount, currency);
        mockMvc.perform(post(BASE_URL + "/" + accountId + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── tests ──────────────────────────────────────────────────────────────────

    @Test
    void createAccount() throws Exception {
        mockMvc.perform(post(BASE_URL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber());
    }

    @Test
    void getAllAccounts_emptyInitially() throws Exception {
        // given — fresh context, no accounts created

        // when / then
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountIds").isArray());
    }

    @Test
    void getAllAccounts_returnsCreatedAccounts() throws Exception {
        // given
        String before = mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int baseCount = objectMapper.readTree(before).get("accountIds").size();
        Long id1 = createAccountAndGetId();
        Long id2 = createAccountAndGetId();

        // when / then
        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountIds").isArray())
                .andExpect(jsonPath("$.accountIds.length()").value(baseCount + 2))
                .andExpect(jsonPath("$.accountIds[" + baseCount + "]").value(id1))
                .andExpect(jsonPath("$.accountIds[" + (baseCount + 1) + "]").value(id2));
    }

    @Test
    void getBalancesReturnsAllCurrenciesAsZero() throws Exception {
        // given
        Long id = createAccountAndGetId();

        // when / then
        mockMvc.perform(get(BASE_URL + "/" + id + "/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(id))
                .andExpect(jsonPath("$.balances.EUR").value(0.00))
                .andExpect(jsonPath("$.balances.USD").value(0.00))
                .andExpect(jsonPath("$.balances.SEK").value(0.00))
                .andExpect(jsonPath("$.balances.GBP").value(0.00));
    }

    @Test
    void deposit_addsMoneyToAccount() throws Exception {
        // given
        Long id = createAccountAndGetId();
        MoneyRequest req = new MoneyRequest(new BigDecimal("100.50"), SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.EUR").value(100.50));
    }

    @Test
    void withdraw_debitsMoneyFromAccount() throws Exception {
        // given
        willDoNothing().given(externalLoggingClient).logWithdrawal();
        Long id = createAccountAndGetId();
        deposit(id, new BigDecimal("200.00"), SupportedCurrency.EUR);
        MoneyRequest req = new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.EUR").value(150.00));
    }

    @Test
    void withdraw_failsOnInsufficientFunds() throws Exception {
        // given
        willDoNothing().given(externalLoggingClient).logWithdrawal();
        Long id = createAccountAndGetId();
        deposit(id, new BigDecimal("10.00"), SupportedCurrency.EUR);
        MoneyRequest req = new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        containsString(id.toString())))
                .andExpect(jsonPath("$.detail").value(
                        containsString("EUR")))
                .andExpect(jsonPath("$.detail").value(
                        containsString("50")));
    }

    @Test
    void withdraw_failsWhenExternalCallFails() throws Exception {
        // given
        willThrow(new ExternalLoggingFailedException("external fail"))
                .given(externalLoggingClient).logWithdrawal();
        Long id = createAccountAndGetId();
        deposit(id, new BigDecimal("200.00"), SupportedCurrency.EUR);
        MoneyRequest req = new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail")
                        .value("The request could not be processed due to an external dependency failure."));
    }

    @Test
    void exchange_convertsMoneyBetweenCurrencies() throws Exception {
        // given
        Long id = createAccountAndGetId();
        deposit(id, new BigDecimal("100.00"), SupportedCurrency.EUR);
        ExchangeRequest req = new ExchangeRequest(
                new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.USD);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.EUR").value(50.00))
                .andExpect(jsonPath("$.balances.USD").value(55.00));
    }

    @Test
    void exchange_failsWhenSameCurrency() throws Exception {
        // given
        Long id = createAccountAndGetId();
        ExchangeRequest req = new ExchangeRequest(
                new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString(id.toString())))
                .andExpect(jsonPath("$.detail").value(containsString("EUR")));
    }

    @Test
    void validationError_missingAmount() throws Exception {
        // given
        Long id = createAccountAndGetId();

        // when / then — amount is null, detail names the field and constraint
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"EUR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("amount")))
                .andExpect(jsonPath("$.detail").value(containsString("required")))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations[0]").value(containsString("amount")));
    }

    @Test
    void validationError_negativeAmount() throws Exception {
        // given
        Long id = createAccountAndGetId();
        MoneyRequest req = new MoneyRequest(new BigDecimal("-5.00"), SupportedCurrency.EUR);

        // when / then — detail names the field, constraint, and rejected value
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("amount")))
                .andExpect(jsonPath("$.detail").value(containsString("0.01")))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void validationError_zeroAmount() throws Exception {
        // given
        Long id = createAccountAndGetId();
        MoneyRequest req = new MoneyRequest(BigDecimal.ZERO, SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("amount")))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void accountNotFound() throws Exception {
        // given — account 999 does not exist

        // when / then
        mockMvc.perform(get(BASE_URL + "/999/balances"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("999")));
    }

    @Test
    void idempotency_depositReturnsSameResponseOnReplay() throws Exception {
        // given
        Long id = createAccountAndGetId();
        String body = objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("100.00"), SupportedCurrency.EUR));

        MvcResult first = mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Idempotency-Key", "dep-key-1"))
                .andExpect(status().isOk())
                .andReturn();

        // when — replay with same key
        MvcResult second = mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Idempotency-Key", "dep-key-1"))
                .andExpect(status().isOk())
                .andReturn();

        // then — identical response, balance deposited only once
        then(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        mockMvc.perform(get(BASE_URL + "/" + id + "/balances"))
                .andExpect(jsonPath("$.balances.EUR").value(100.00));
    }

    @Test
    void idempotency_withdrawReturnsSameResponseOnReplay() throws Exception {
        // given
        willDoNothing().given(externalLoggingClient).logWithdrawal();
        Long id = createAccountAndGetId();
        deposit(id, new BigDecimal("200.00"), SupportedCurrency.EUR);
        String body = objectMapper.writeValueAsString(new MoneyRequest(new BigDecimal("50.00"), SupportedCurrency.EUR));

        MvcResult first = mockMvc.perform(post(BASE_URL + "/" + id + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Idempotency-Key", "wd-key-1"))
                .andExpect(status().isOk())
                .andReturn();

        // when — replay with same key
        MvcResult second = mockMvc.perform(post(BASE_URL + "/" + id + "/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Idempotency-Key", "wd-key-1"))
                .andExpect(status().isOk())
                .andReturn();

        // then — identical response, balance withdrawn only once
        then(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        mockMvc.perform(get(BASE_URL + "/" + id + "/balances"))
                .andExpect(jsonPath("$.balances.EUR").value(150.00));
    }

    @Test
    void idempotency_exchangeReturnsSameResponseOnReplay() throws Exception {
        // given
        Long id = createAccountAndGetId();
        deposit(id, new BigDecimal("100.00"), SupportedCurrency.EUR);
        String body = objectMapper.writeValueAsString(
                new ExchangeRequest(new BigDecimal("50.00"), SupportedCurrency.EUR, SupportedCurrency.USD));

        MvcResult first = mockMvc.perform(post(BASE_URL + "/" + id + "/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Idempotency-Key", "ex-key-1"))
                .andExpect(status().isOk())
                .andReturn();

        // when — replay with same key
        MvcResult second = mockMvc.perform(post(BASE_URL + "/" + id + "/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Idempotency-Key", "ex-key-1"))
                .andExpect(status().isOk())
                .andReturn();

        // then — identical response, exchange executed only once
        then(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        mockMvc.perform(get(BASE_URL + "/" + id + "/balances"))
                .andExpect(jsonPath("$.balances.EUR").value(50.00))
                .andExpect(jsonPath("$.balances.USD").value(55.00));
    }

    // ── bad-input / protocol error tests ──────────────────────────────────────

    @Test
    void validationError_negativeAmount_returnsBadRequestWithDetail() throws Exception {
        // given
        Long id = createAccountAndGetId();
        MoneyRequest req = new MoneyRequest(new BigDecimal("-5.00"), SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("amount")))
                .andExpect(jsonPath("$.detail").value(containsString("0.01")))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void validationError_zeroAmount_returnsBadRequestWithDetail() throws Exception {
        // given
        Long id = createAccountAndGetId();
        MoneyRequest req = new MoneyRequest(BigDecimal.ZERO, SupportedCurrency.EUR);

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("amount")))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void malformedJson_returnsBadRequest() throws Exception {
        // given
        Long id = createAccountAndGetId();

        // when / then
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("cannot be parsed")));
    }

    @Test
    void invalidCurrencyEnumInBody_returnsBadRequest() throws Exception {
        // given
        Long id = createAccountAndGetId();

        // when / then — detail names the rejected value, field, and accepted currencies
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"currency\": \"XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("XYZ")))
                .andExpect(jsonPath("$.detail").value(containsString("currency")))
                .andExpect(jsonPath("$.detail").value(containsString("EUR")));
    }

    @Test
    void nonNumericAccountId_returnsBadRequest() throws Exception {
        // given — "not-a-number" cannot be coerced to Long

        // when / then — detail names the parameter and the rejected value
        mockMvc.perform(get(BASE_URL + "/not-a-number/balances"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("not-a-number")))
                .andExpect(jsonPath("$.detail").value(containsString("accountId")));
    }

    @Test
    void wrongHttpMethod_returnsMethodNotAllowed() throws Exception {
        // given — DELETE is not mapped for /api/v1/accounts

        // when / then
        mockMvc.perform(delete(BASE_URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.detail").value("HTTP method not allowed for this endpoint."));
    }

    @Test
    void missingContentType_returnsUnsupportedMediaType() throws Exception {
        // given
        Long id = createAccountAndGetId();
        MoneyRequest req = new MoneyRequest(new BigDecimal("100.00"), SupportedCurrency.EUR);

        // when / then — no Content-Type defaults to application/octet-stream
        mockMvc.perform(post(BASE_URL + "/" + id + "/deposit")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Content type not supported. Use application/json."));
    }
}
