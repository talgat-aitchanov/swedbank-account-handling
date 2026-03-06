# Swedbank Account Handling

A self-contained Spring Boot microservice for bank account management. Supports multi-currency balances (EUR, USD, SEK, GBP), deposits, withdrawals, currency exchange, and idempotent operations.

---

## Tech Stack

| Technology | Version |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.0 |
| Gradle | 8.x (wrapper included) |
| H2 Database | file-based, externally accessible |
| Liquibase | schema migrations |
| springdoc OpenAPI | Swagger UI |

---

## Prerequisites

- **Java 17** installed and available on `PATH`
- No other external dependencies — H2 is embedded

---

## Building the Project

```bash
./gradlew clean build
```

This runs compilation, unit tests, and integration tests.

---

## Running the Application

```bash
./gradlew bootRun
```

Or run the fat JAR directly:

```bash
./gradlew bootJar
java -jar build/libs/swedbank-account-handling-1.0-SNAPSHOT.jar
```

The application starts on **http://localhost:8080** by default.

On startup:
- Liquibase applies all DB migrations automatically
- H2 file database is created at `./data/bankdb`
- H2 TCP server starts on port **9092** for external tool access

---

## Useful URLs

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI — interactive API docs |
| http://localhost:8080/v3/api-docs | OpenAPI JSON spec |
| http://localhost:8080/h2-console | H2 web console |

### H2 Console Login

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:file:./data/bankdb` |
| Username | `sa` |
| Password | *(empty)* |

> The database file is stored at `./data/bankdb.mv.db` relative to the working directory. Data persists across application restarts — Liquibase tracks applied migrations via the `DATABASECHANGELOG` table and skips already-applied changesets on subsequent runs.

### External JDBC Access (DBeaver, IntelliJ, etc.)

While the app is running, connect via TCP:

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:tcp://localhost:9092/./data/bankdb` |
| Driver | `org.h2.Driver` |
| Username | `sa` |
| Password | *(empty)* |

---

## Running Tests

```bash
# Unit tests only
./gradlew test

# Unit tests (explicit task)
./gradlew unitTest

# Integration tests only
./gradlew integrationTest

# All tests (unit + integration)
./gradlew check
```

---

## API Reference

Base path: `/api/v1/accounts`

### Idempotency

All mutating endpoints (`deposit`, `withdraw`, `exchange`) support an optional **`Idempotency-Key`** request header.

**Contract:**
- The key is scoped per operation type (`DEPOSIT`, `WITHDRAW`, `EXCHANGE`). The same key can be used independently for a deposit and a withdrawal — they will not interfere with each other.
- When a request is first processed with a given key, the response is persisted and returned.
- Any **subsequent** request with the **same key and same operation type** returns the **exact same response that was stored on the first call** — the operation is **not re-executed**.
- The stored response is a **point-in-time snapshot** of the account balances at the moment of the first call. It does **not** reflect any balance changes made by later requests with different keys (or no key).
- Omitting the header (or sending an empty value) disables idempotency for that call — the operation always executes.

**How to deposit the same amount multiple times:**

Each independent deposit must use a **different key** (or no key at all). The key identifies a specific *request attempt*, not a recurring operation type.

```
# First deposit of 100 EUR — use a unique key per request
POST /deposit  Idempotency-Key: dep-2026-03-06-001   → executes, balance EUR 100.00

# Second deposit of 100 EUR — new key = new operation
POST /deposit  Idempotency-Key: dep-2026-03-06-002   → executes, balance EUR 200.00

# Retry of the FIRST request (e.g. after network timeout) — same key = replay
POST /deposit  Idempotency-Key: dep-2026-03-06-001   → cached, balance EUR 100.00 (snapshot)

# Deposit with no key — idempotency disabled, always executes
POST /deposit  (no header)                           → executes every time
```

A good key strategy: `{operation}-{date}-{sequence}` or a UUID generated client-side per request attempt.

**Example — why the snapshot may look "stale":**

```
# 1. Deposit 0.01 EUR with key "A" — balance becomes EUR 0.01
POST /deposit  Idempotency-Key: A   → { "EUR": 0.01 }  ← stored for key "A"

# 2. Deposit 500.00 EUR with NO key — balance becomes EUR 500.01
POST /deposit  (no header)          → { "EUR": 500.01 }

# 3. Repeat key "A" — operation not re-executed, returns stored snapshot
POST /deposit  Idempotency-Key: A   → { "EUR": 0.01 }  ← snapshot from step 1
```

To see the current balance at any time: `GET /api/v1/accounts/{id}/balances`

Error responses follow **RFC 7807 Problem Details** JSON format.

---

### 1. Create Account

```
POST /api/v1/accounts
```

**Response `201 Created`:**
```json
{
  "accountId": 1
}
```

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts | jq
```

---

### 2. Get All Accounts

```
GET /api/v1/accounts
```

Returns a list of all account IDs (no balance details). Returns an empty list if no accounts exist.

**Response `200 OK`:**
```json
{
  "accountIds": [1, 2, 3]
}
```

**Example:**
```bash
curl -s http://localhost:8080/api/v1/accounts | jq
```

---

### 3. Get Balances

```
GET /api/v1/accounts/{accountId}/balances
```

Returns balances for all four supported currencies. Missing currencies are returned as `0.00`.

**Response `200 OK`:**
```json
{
  "accountId": 1,
  "balances": {
    "EUR": 0.00,
    "USD": 0.00,
    "SEK": 0.00,
    "GBP": 0.00
  }
}
```

**Example:**
```bash
curl -s http://localhost:8080/api/v1/accounts/1/balances | jq
```

---

### 4. Deposit

```
POST /api/v1/accounts/{accountId}/deposit
```

Adds money to the specified currency balance.

**Request body:**
```json
{
  "amount": 500.00,
  "currency": "EUR"
}
```

**Response `200 OK`:** full balance snapshot (same shape as Get Balances)

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00, "currency": "EUR"}' | jq
```

**With idempotency key:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: dep-001" \
  -d '{"amount": 500.00, "currency": "EUR"}' | jq
```

---

### 5. Withdraw

```
POST /api/v1/accounts/{accountId}/withdraw
```

Debits money from the specified currency balance. Before processing, the service makes an HTTP call to an external system (`external.logging.url`). If the call fails, the withdrawal is rejected with `502 Bad Gateway`.

No automatic currency conversion — the requested currency must have sufficient funds.

**Request body:**
```json
{
  "amount": 100.00,
  "currency": "EUR"
}
```

**Response `200 OK`:** full balance snapshot

**Errors:**
- `400 Bad Request` — insufficient funds
- `502 Bad Gateway` — external dependency failure

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/1/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "currency": "EUR"}' | jq
```

---

### 6. Exchange

```
POST /api/v1/accounts/{accountId}/exchange
```

Converts an amount from one currency to another within the same account using fixed exchange rates (EUR as base).

| Currency | Rate (vs EUR) |
|---|---|
| EUR | 1.00 |
| USD | 1.10 |
| SEK | 11.20 |
| GBP | 0.86 |

`fromCurrency` and `toCurrency` must be different.

**Request body:**
```json
{
  "amount": 100.00,
  "fromCurrency": "EUR",
  "toCurrency": "USD"
}
```

**Response `200 OK`:** full balance snapshot

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/1/exchange \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "fromCurrency": "EUR", "toCurrency": "USD"}' | jq
```

---

## End-to-End Walkthrough

```bash
# 1. Create an account
curl -s -X POST http://localhost:8080/api/v1/accounts | jq
# => { "accountId": 1 }

# 2. Check initial balances (all zero)
curl -s http://localhost:8080/api/v1/accounts/1/balances | jq

# 3. Deposit 500 EUR
curl -s -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00, "currency": "EUR"}' | jq

# 4. Deposit 200 USD
curl -s -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 200.00, "currency": "USD"}' | jq

# 5. Withdraw 50 EUR
curl -s -X POST http://localhost:8080/api/v1/accounts/1/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00, "currency": "EUR"}' | jq

# 6. Exchange 100 EUR -> GBP
curl -s -X POST http://localhost:8080/api/v1/accounts/1/exchange \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "fromCurrency": "EUR", "toCurrency": "GBP"}' | jq

# 7. Check final balances
curl -s http://localhost:8080/api/v1/accounts/1/balances | jq
# EUR: 350.00, USD: 200.00, SEK: 0.00, GBP: 86.00
```

---

## Error Response Format (RFC 7807)

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Insufficient funds for the requested operation.",
  "instance": "/api/v1/accounts/1/withdraw"
}
```

| HTTP Status | Scenario |
|---|---|
| `400 Bad Request` | Validation error, invalid/missing fields, malformed JSON, unknown enum value, insufficient funds, same-currency exchange |
| `404 Not Found` | Account does not exist |
| `405 Method Not Allowed` | Wrong HTTP method for the endpoint |
| `415 Unsupported Media Type` | Missing or wrong `Content-Type` (must be `application/json`) |
| `502 Bad Gateway` | External logging call failed |
| `500 Internal Server Error` | Unexpected error |

---

## Supported Currencies

`EUR` · `USD` · `SEK` · `GBP`

---

## Configuration

Key properties in `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/bankdb;AUTO_SERVER=TRUE

external:
  logging:
    url: https://api.frankfurter.dev/v1/latest   # external call made before every withdrawal

h2:
  tcp:
    enabled: true   # set to false to suppress TCP server (e.g. in tests)
```

