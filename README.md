# PortfolioBro

Java 21 / Spring Boot backend for a paper investment portfolio tracker. Portfolio CRUD is backed by PostgreSQL, and a pure Java accounting engine reconstructs positions from ordered trades. Trades are persisted with idempotency and overselling protection. Market-data analytics and authentication are subsequent milestones.

## Run locally

Prerequisites: JDK 21, Docker Engine or Docker Desktop with Compose, and internet access for the first dependency download. Maven Wrapper is included; no global Maven installation is needed.

```sh
cp .env.example .env
# Edit .env and replace the example password.
set -a
. ./.env
set +a
docker compose up -d --wait
./mvnw spring-boot:run
```

Compose reads `.env` automatically; the application requires the variables to be exported as above. Keep `.env` private. Application and database bind to localhost. This single-user API has no authentication and should remain local.

- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

```sh
curl -i http://localhost:8080/api/v1/portfolios \
  -H 'Content-Type: application/json' \
  -d '{"name":"Long term","description":"Paper investments"}'

curl 'http://localhost:8080/api/v1/portfolios?page=0&size=20'

# Replace UUID with the ID returned by POST.
curl http://localhost:8080/api/v1/portfolios/UUID
curl -X PATCH http://localhost:8080/api/v1/portfolios/UUID \
  -H 'Content-Type: application/json' -d '{"name":"Retirement"}'
curl -X PATCH http://localhost:8080/api/v1/portfolios/UUID \
  -H 'Content-Type: application/json' -d '{"description":""}'
curl -i -X DELETE http://localhost:8080/api/v1/portfolios/UUID
```

POST returns 201 and a Location header; reads and updates return 200; deletion returns 204. Missing resources return 404. Invalid input returns 400 with `application/problem+json`, an application `code`, and field errors when available.

Names are stripped of surrounding whitespace and must contain 1–100 characters. Descriptions allow up to 1,000 characters. PATCH requires a non-null name or description; omitted/null fields are unchanged and an empty description clears it. Currency is server-owned USD. All timestamps use UTC. Pagination defaults to page 0, size 20 (maximum 100), sorted by creation time then ID. Responses use a stable `content`, `page`, `size`, `totalElements`, `totalPages` envelope.

## Architecture

```text
HTTP → controller (validation/DTOs)
     → service (transaction boundary)
     → repository (JPA)
     → PostgreSQL
```

Packages under `com.portfoliobro`: `controller`, `service`, `repository`, `domain`, `dto`, `exception`, and `config`. JPA entities do not cross the API boundary. Updates rely on transactional dirty checking. Flyway owns schema changes; Hibernate validates rather than modifies the schema. Open Session in View is disabled.

The `portfolio` table stores UUID ID, name, description, USD currency, and creation/update timestamps. Database constraints enforce required fields, trimmed nonempty names, lengths, and USD currency. An index supports deterministic list ordering. The `trade` table references its portfolio with restricted deletion. Portfolios containing trades cannot be deleted (409). Trades are append-only through the API, indexed by portfolio/sequence and portfolio/ticker/sequence, and uniquely constrained by portfolio/idempotency key.

## Test and build

```sh
./mvnw test       # Unit/controller tests; no database needed
./mvnw verify     # Also runs PostgreSQL integration tests; Docker must be running
./mvnw package -DskipTests
```

Unit tests cover normalization, server-owned fields, PATCH behavior, errors, and pagination validation. Integration tests use disposable PostgreSQL 17 containers with real Flyway migrations and exercise HTTP CRUD, independent-request persistence, database constraints, documentation, and health. Integration tests intentionally fail if Docker is unavailable. CI runs the full `verify` lifecycle.

Run the packaged application with the same exported database variables:

```sh
java -jar target/portfolio-bro-0.0.1-SNAPSHOT.jar
```

To check restart persistence, create a portfolio, stop/restart the application, and retrieve the same ID. `docker compose down` preserves database data. Changing initialization credentials after the volume has been created does not change existing database users; keep the original credentials or manage the database user explicitly.

## Development steps

1. **Foundation and portfolios (complete):** Maven Wrapper, PostgreSQL Compose, Flyway, CRUD, errors, OpenAPI, automated tests, and CI.
2. **Accounting (complete):** pure Java average-cost calculator with BigDecimal, fees, partial/full sells, fractional quantities, and documented rounding.
3. **Trade persistence (complete):** chronological immutable trades, portfolio row locks, overselling prevention, idempotency keys, and concurrent PostgreSQL tests.
4. **Analytics:** reconstruct holdings and expose realized/unrealized P&L using deterministic mock prices with explicit freshness/missing-data handling.
5. **Later:** live prices, authentication/ownership before public hosting, and a minimal dashboard.

Use small capability-focused commits with their tests. Add new migrations after a migration has been applied; do not rewrite migration history. No performance claims are made without measurements.

## Average-cost accounting

`AverageCostCalculator` accepts an ordered `List<TradeEntry>` and returns an immutable ticker-to-`PositionSnapshot` map. It has no Spring, database, or market-data dependencies. Replay is stateless and includes closed positions so realized P&L is retained. Inputs are immutable and tickers are normalized to uppercase.

For a BUY, add shares and add `shares × price + fees` to remaining cost basis. Average cost is remaining basis divided by shares. For a SELL, reject quantities exceeding the position at that point, remove `remaining basis × sold shares / held shares`, and add `shares × price − fees − allocated basis` to realized P&L. Fees may exceed sale proceeds, producing a loss. Reopening a position preserves previous realized P&L. This is average-cost paper accounting, not FIFO or tax reporting.

Trades must arrive in nondecreasing timestamp order across the portfolio; equal timestamps preserve input order. Replay never sorts or repairs invalid history. The future persistence service will load portfolio sequence order and validate future timestamps against an injected clock. The calculator deliberately has no wall-clock dependency.

### Precision policy

- All financial values use `BigDecimal`; construct them from decimal strings, never `double`.
- Shares must be positive and fit `NUMERIC(19,8)`; prices must be positive and fees nonnegative, both fitting `NUMERIC(19,6)`. Excess meaningful precision is rejected, not rounded. Equivalent trailing zeros are accepted.
- Multiplication and addition are exact. Basis allocation and average-cost division use scale 18 and `HALF_EVEN` rounding. Internal results are not rounded to cents.
- On full liquidation, remove all remaining basis exactly, absorbing any residual from earlier partial-sale divisions. Quantity, basis, and average cost become zero.
- Computed monetary amounts must have magnitude below `10^30`; unsupported results fail explicitly. Aggregate share quantities are not truncated to the per-trade input range.
- Future analytics DTOs will round money to two decimals and average cost to six decimals after aggregation, serializing decimals as strings. These endpoints are not yet implemented.

Example: BUY 10 at $100 with $2 fees, BUY 5 at $120 with $1 fees → 15 shares and $1,603 basis. SELL 6 at $130 with $2 fees → 9 shares, $961.80 basis, and $136.80 realized gain. Selling the final 9 at $125 without fees leaves exactly zero shares/basis and $300 total realized gain.

The calculator tests cover these examples, fractional shares, both half-even tie directions, repeated-sale residuals, closed/reopened positions, fees, losses, independent tickers, overselling, chronology, and invalid precision. Market valuation and unrealized P&L require the later price-provider milestone.

## Record trades

```sh
curl -i http://localhost:8080/api/v1/portfolios/UUID/trades \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 12345678-1234-4234-8234-123456789abc' \
  -d '{"side":"BUY","ticker":"AAPL","shares":"10","pricePerShare":"100","fees":"2","executedAt":"2026-01-01T12:00:00Z"}'
curl 'http://localhost:8080/api/v1/portfolios/UUID/trades?ticker=AAPL&page=0&size=20'
```

Use a new UUID `Idempotency-Key` for each intended trade and reuse it when retrying that trade. A new trade returns 201; an identical retry returns 200 and the original trade, including after newer trades. Decimal equivalence and ticker normalization are respected. Reusing the key with a different payload returns 409 `IDEMPOTENCY_CONFLICT`. Failed submissions do not consume a key. Keys are scoped to a portfolio. Trade responses serialize financial values as decimal strings.

Fees default to zero. Shares/prices must be positive; fees cannot be negative. Timestamps must be no later than the server clock, use at most microsecond precision, and be nondecreasing across the portfolio. Equal timestamps use portfolio-local sequence order. Backdating returns 409 `TRADE_CHRONOLOGY`; overselling returns 409 `OVERSELL`. Trade edits/deletes and backdated imports are intentionally unsupported.

Recording runs in one database transaction: acquire a portfolio row lock, check the idempotency key, replay accepted trades, validate the proposed trade, and insert it. Locking the portfolio also covers the first trade in a position. Concurrent SELLs cannot both spend the same shares. Deletion acquires the same lock, and a foreign key prevents removing history. Lock waits are bounded to two seconds and return 503 `LOCK_TIMEOUT`; retry using the same key. No external market-data calls happen while this lock is held.

PostgreSQL integration tests exercise concurrent overselling, concurrent duplicate requests, lock timeout, retry equivalence, rollback after rejection, sequence ordering, foreign keys, database checks, and portfolio-scoped keys.
