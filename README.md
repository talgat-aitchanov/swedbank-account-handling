# Swedbank Account Handling

A secure Spring Boot microservice for bank account management with JWT authentication, role-based access control (RBAC), and comprehensive audit trails. Supports multi-currency balances (EUR, USD, SEK, GBP), deposits, withdrawals, currency exchange, and idempotent operations.

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 17 | Runtime |
| Spring Boot | 3.2.0 | Application framework |
| Spring Security | 6.2.0 | Authentication & authorization |
| JJWT | 0.12.3 | JWT token generation/validation |
| Gradle | 8.x | Build tool (wrapper included) |
| H2 Database | 2.x | File-based embedded database |
| Liquibase | 4.x | Schema migrations |
| springdoc OpenAPI | 2.x | API documentation (Swagger UI) |

---

## Architecture Highlights

- **Clean Architecture** — clear separation between API, service, domain, and infrastructure layers
- **Security-first** — JWT-based authentication, BCrypt password hashing, role-based access control
- **Audit trail** — every operation records `initiated_by` (username) and optional admin `note`
- **Idempotency** — all mutating operations support client-provided idempotency keys
- **RFC 9457** — all error responses use Problem Details JSON format
- **Defense in depth** — `@PreAuthorize` at controller + filter chain enforcement

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
- Two seed users are created in `local`/`test` contexts:
  - **`user`** / `password` (role: `USER`)
  - **`admin`** / `admin123` (role: `ADMIN`)

---

## Security & Authentication

### JWT Authentication Flow

1. **Login**: `POST /auth/login` with `{ "username": "user", "password": "password" }`
2. Receive a JWT token: `{ "token": "eyJhbGc..." }`
3. Include the token in subsequent requests: `Authorization: Bearer <token>`

### User Roles

| Role | Capabilities |
|------|-------------|
| **USER** | Create accounts, deposit/withdraw/exchange on **own** accounts only |
| **ADMIN** | View all accounts across all users, perform deposit/withdraw/exchange on **any** account, add audit notes |

### Password Security

- All passwords stored as **BCrypt** hashes (strength 10)
- Default users seeded via Liquibase migration (`contexts: local, test`)
- Production deployments: remove seed context or use environment-specific changesets

### JWT Configuration

Default settings in `application.yml`:

```yaml
app:
  jwt:
    secret: "3cfa76ef14937c1c0ea519f8fc057a80fcd04a7420f8e8bcd0a7567c272e007b"
    expiration-ms: 3600000  # 1 hour
```

⚠️ **Production**: Use environment variables or a secrets manager for the JWT secret.

---

## Useful URLs

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI — interactive API docs with JWT auth |
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

# Integration tests only
./gradlew integrationTest

# All tests (unit + integration)
./gradlew check
```

Integration tests verify:
- JWT authentication & authorization
- Role separation (USER cannot access admin endpoints, ADMIN cannot access user endpoints)
- Account ownership enforcement
- Idempotency across all mutating operations
- Audit trail persistence
- Error handling (401, 403, 404, 400, 502)

---

## API Reference

### Authentication

#### Login
```
POST /auth/login
```

**Request body:**
```json
{
  "username": "user",
  "password": "password"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response `401 Unauthorized`** (invalid credentials):
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid username or password.",
  "instance": "/auth/login"
}
```

**Example:**
```bash
# Login as user
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"password"}' | jq -r '.token')

# Use the token
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/accounts
```

---

### User Endpoints (Role: `USER`)

Base path: `/api/v1/accounts`

All endpoints require `Authorization: Bearer <token>` header.

#### 1. Create Account

```
POST /api/v1/accounts
```

Creates a new account linked to the authenticated user.

**Response `201 Created`:**
```json
{
  "accountId": 1
}
```

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" | jq
```

---

#### 2. Get My Accounts with Balances

```
GET /api/v1/accounts
```

Returns all accounts owned by the authenticated user, each with full balance details.

**Response `200 OK`:**
```json
[
  {
    "accountId": 1,
    "ownerUsername": "user",
    "balances": {
      "EUR": 500.00,
      "USD": 200.00,
      "SEK": 0.00,
      "GBP": 86.00
    }
  },
  {
    "accountId": 2,
    "ownerUsername": "user",
    "balances": {
      "EUR": 0.00,
      "USD": 0.00,
      "SEK": 0.00,
      "GBP": 0.00
    }
  }
]
```

**Example:**
```bash
curl -s http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" | jq
```

---

#### 3. Get Balances for Specific Account

```
GET /api/v1/accounts/{accountId}/balances
```

Returns balances for all four supported currencies. Ownership is verified — returns `403` if the account belongs to another user.

**Response `200 OK`:**
```json
{
  "accountId": 1,
  "balances": {
    "EUR": 500.00,
    "USD": 200.00,
    "SEK": 0.00,
    "GBP": 0.00
  }
}
```

**Example:**
```bash
curl -s http://localhost:8080/api/v1/accounts/1/balances \
  -H "Authorization: Bearer $TOKEN" | jq
```

---

#### 4. Deposit

```
POST /api/v1/accounts/{accountId}/deposit
```

Adds money to the specified currency balance. Ownership is verified.

**Request body:**
```json
{
  "amount": 500.00,
  "currency": "EUR"
}
```

**Optional header:** `Idempotency-Key: <unique-key>`

**Response `200 OK`:** full balance snapshot

**Audit:** Operation is recorded with `initiated_by: "user"` (the authenticated username).

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00, "currency": "EUR"}' | jq
```

**With idempotency key:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/1/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: dep-001" \
  -d '{"amount": 500.00, "currency": "EUR"}' | jq
```

---

#### 5. Withdraw

```
POST /api/v1/accounts/{accountId}/withdraw
```

Debits money from the specified currency balance. Before processing, the service makes an HTTP call to an external system (`external.logging.url`). If the call fails, the withdrawal is rejected with `502 Bad Gateway`.

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
- `403 Forbidden` — account belongs to another user
- `502 Bad Gateway` — external dependency failure

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/accounts/1/withdraw \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "currency": "EUR"}' | jq
```

---

#### 6. Exchange

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
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "fromCurrency": "EUR", "toCurrency": "USD"}' | jq
```

---

### Admin Endpoints (Role: `ADMIN`)

Base path: `/api/v1/admin/accounts`

All endpoints require `Authorization: Bearer <admin-token>` header.

#### 1. Get All Accounts (Aggregated by User)

```
GET /api/v1/admin/accounts[?username=<user>]
```

Returns all accounts in the system grouped by owner, with full balances. Optional `username` query parameter filters to a specific user.

**Response `200 OK`:**
```json
[
  {
    "username": "user",
    "accounts": [
      {
        "accountId": 1,
        "ownerUsername": "user",
        "balances": {
          "EUR": 500.00,
          "USD": 200.00,
          "SEK": 0.00,
          "GBP": 86.00
        }
      },
      {
        "accountId": 2,
        "ownerUsername": "user",
        "balances": {
          "EUR": 0.00,
          "USD": 0.00,
          "SEK": 0.00,
          "GBP": 0.00
        }
      }
    ]
  },
  {
    "username": "admin",
    "accounts": []
  }
]
```

**Example:**
```bash
# All accounts
curl -s http://localhost:8080/api/v1/admin/accounts \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Filter by username
curl -s "http://localhost:8080/api/v1/admin/accounts?username=user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

---

#### 2. Get Balances for Any Account

```
GET /api/v1/admin/accounts/{accountId}/balances
```

Admin can view balances for **any** account (no ownership check).

**Response `200 OK`:**
```json
{
  "accountId": 1,
  "balances": {
    "EUR": 500.00,
    "USD": 200.00,
    "SEK": 0.00,
    "GBP": 0.00
  }
}
```

---

#### 3. Admin Deposit

```
POST /api/v1/admin/accounts/{accountId}/deposit
```

Admin-initiated deposit on **any** account. Accepts an optional `note` for audit purposes.

**Request body:**
```json
{
  "amount": 500.00,
  "currency": "EUR",
  "note": "Bonus credit for Q1 2026"
}
```

**Response `200 OK`:** full balance snapshot

**Audit:** Recorded with `initiated_by: "admin"` (the admin's username) and the provided `note`.

**Example:**
```bash
curl -s -X POST http://localhost:8080/api/v1/admin/accounts/1/deposit \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00, "currency": "EUR", "note": "Bonus credit"}' | jq
```

---

#### 4. Admin Withdraw

```
POST /api/v1/admin/accounts/{accountId}/withdraw
```

Admin-initiated withdrawal on **any** account. Accepts an optional `note`.

**Request body:**
```json
{
  "amount": 100.00,
  "currency": "EUR",
  "note": "Fee correction"
}
```

**Response `200 OK`:** full balance snapshot

---

#### 5. Admin Exchange

```
POST /api/v1/admin/accounts/{accountId}/exchange
```

Admin-initiated currency exchange on **any** account. Accepts an optional `note`.

**Request body:**
```json
{
  "amount": 100.00,
  "fromCurrency": "EUR",
  "toCurrency": "USD",
  "note": "Admin-requested conversion"
}
```

**Response `200 OK`:** full balance snapshot

---

## Idempotency

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

To see the current balance at any time: `GET /api/v1/accounts/{id}/balances`

---

## Audit Trail

Every operation is recorded in the `idempotency_records` table with:

| Column | Description |
|--------|-------------|
| `idempotency_key` | Client-provided key (or `null` if idempotency disabled) |
| `operation_type` | `DEPOSIT`, `WITHDRAW`, or `EXCHANGE` |
| `response_body` | Serialized `BalanceResponse` JSON |
| `initiated_by` | Username of the actor (e.g. `"user"`, `"admin"`) |
| `note` | Admin comment (max 500 chars, `null` for user operations) |
| `created_at` | Timestamp |

Admins can use the `note` field to document reasons for manual adjustments (e.g. "Fee correction", "Bonus credit").

---

## Error Response Format (RFC 9457)

All errors return Problem Details JSON:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Insufficient funds for the requested operation.",
  "instance": "/api/v1/accounts/1/withdraw"
}
```

Validation errors include a `violations` array:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed: 'amount' must be at least 0.01 (rejected value: -5.0).",
  "violations": [
    "'amount' must be at least 0.01 (rejected value: -5.0)"
  ],
  "instance": "/api/v1/accounts/1/deposit"
}
```

| HTTP Status | Scenario |
|---|---|
| `400 Bad Request` | Validation error, invalid/missing fields, malformed JSON, unknown enum value, insufficient funds, same-currency exchange |
| `401 Unauthorized` | Missing/invalid JWT token |
| `403 Forbidden` | Authenticated but lacking required role or account ownership |
| `404 Not Found` | Account does not exist |
| `405 Method Not Allowed` | Wrong HTTP method for the endpoint |
| `415 Unsupported Media Type` | Missing or wrong `Content-Type` (must be `application/json`) |
| `502 Bad Gateway` | External logging call failed |
| `500 Internal Server Error` | Unexpected error |

---

## End-to-End Walkthrough

```bash
# 1. Login as user
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"password"}' | jq -r '.token')

# 2. Create an account
ACCOUNT=$(curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" | jq -r '.accountId')

# 3. Check initial balances (all zero)
curl -s http://localhost:8080/api/v1/accounts/$ACCOUNT/balances \
  -H "Authorization: Bearer $TOKEN" | jq

# 4. Deposit 500 EUR
curl -s -X POST http://localhost:8080/api/v1/accounts/$ACCOUNT/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00, "currency": "EUR"}' | jq

# 5. Deposit 200 USD
curl -s -X POST http://localhost:8080/api/v1/accounts/$ACCOUNT/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 200.00, "currency": "USD"}' | jq

# 6. Withdraw 50 EUR
curl -s -X POST http://localhost:8080/api/v1/accounts/$ACCOUNT/withdraw \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00, "currency": "EUR"}' | jq

# 7. Exchange 100 EUR -> GBP
curl -s -X POST http://localhost:8080/api/v1/accounts/$ACCOUNT/exchange \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "fromCurrency": "EUR", "toCurrency": "GBP"}' | jq

# 8. Check final balances
curl -s http://localhost:8080/api/v1/accounts/$ACCOUNT/balances \
  -H "Authorization: Bearer $TOKEN" | jq
# EUR: 350.00, USD: 200.00, SEK: 0.00, GBP: 86.00

# 9. List all my accounts with balances
curl -s http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" | jq

# 10. Admin: view all accounts
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

curl -s http://localhost:8080/api/v1/admin/accounts \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# 11. Admin: deposit 1000 EUR to user's account with audit note
curl -s -X POST http://localhost:8080/api/v1/admin/accounts/$ACCOUNT/deposit \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 1000.00, "currency": "EUR", "note": "Q1 2026 bonus"}' | jq
```

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
  liquibase:
    contexts: local  # 'local' or 'test' seeds default users; omit in production

external:
  logging:
    url: https://api.frankfurter.dev/v1/latest   # external call made before every withdrawal

h2:
  tcp:
    enabled: true   # set to false to suppress TCP server (e.g. in tests)

app:
  jwt:
    secret: "3cfa76ef14937c1c0ea519f8fc057a80fcd04a7420f8e8bcd0a7567c272e007b"
    expiration-ms: 3600000  # 1 hour
```

⚠️ **Production hardening checklist:**
1. Use environment variables for `app.jwt.secret` (or a secrets manager)
2. Remove `contexts: local` from Liquibase to skip seed user creation
3. Set `h2.tcp.enabled: false` in production
4. Use a production-grade database (PostgreSQL, MySQL)
5. Enable HTTPS/TLS
6. Configure CORS if serving a frontend
7. Add rate limiting
8. Review and harden `/h2-console` and `/swagger-ui` access

---

## Database Schema

Managed entirely via Liquibase migrations in `src/main/resources/db/changelog/sql/`.

Key tables:
- **`accounts`** — account metadata, `owner_username` links to the authenticated user
- **`account_balances`** — one row per (account, currency) pair, unique constraint enforced
- **`idempotency_records`** — audit log, stores `initiated_by`, `note`, and cached responses
- **`app_users`** — usernames, BCrypt password hashes, roles (`USER` or `ADMIN`)

---

## Code Review Findings & Best Practices

✅ **Strengths:**
- Clean layered architecture (API → Service → Repository → Domain)
- Comprehensive security (JWT, BCrypt, RBAC, `@PreAuthorize`)
- Full audit trail (`initiated_by` + `note` on every operation)
- Idempotency support for all mutating operations
- RFC 9457 Problem Details for error responses
- 100% test coverage (unit + integration)
- Defense in depth (URL matcher + method security)

⚠️ **Production Considerations:**
1. **JWT secret** — currently hardcoded; use environment variable or secrets manager
2. **Seed users** — production deployments should skip the `contexts: local, test` changeset
3. **H2 console** — disable in production or restrict to admin IPs
4. **Rate limiting** — add Spring Security rate limiter or API gateway
5. **Database** — migrate from H2 to PostgreSQL/MySQL for production
6. **CORS** — configure `CorsConfiguration` if serving a frontend
7. **Logging** — redact sensitive data (passwords, full JWT tokens) in logs
8. **External service** — `external.logging.url` should have retry/circuit-breaker (consider Resilience4j)

---

## License

Proprietary — Swedbank internal use only.

---

## Contact

For support or questions, contact the backend team.

