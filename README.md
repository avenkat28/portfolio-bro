# PortfolioBro

Java 21 / Spring Boot backend for a paper investment portfolio tracker. This first working slice provides portfolio CRUD backed by PostgreSQL. Trades, holdings, accounting, market data, and authentication are subsequent milestones.

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

The `portfolio` table stores UUID ID, name, description, USD currency, and creation/update timestamps. Database constraints enforce required fields, trimmed nonempty names, lengths, and USD currency. An index supports deterministic list ordering. No trades table exists yet; consequently every portfolio in this milestone can be deleted. The trade milestone must introduce foreign-key restriction and reject deletion of nonempty portfolios.

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

1. **Foundation and portfolios (this milestone):** Maven Wrapper, PostgreSQL Compose, Flyway, CRUD, errors, OpenAPI, automated tests, and CI.
2. **Accounting:** pure Java average-cost calculator with BigDecimal, fees, partial/full sells, fractional quantities, and documented rounding.
3. **Trade persistence:** chronological immutable trades, portfolio row locks, overselling prevention, idempotency keys, and concurrent PostgreSQL tests.
4. **Analytics:** reconstruct holdings and expose realized/unrealized P&L using deterministic mock prices with explicit freshness/missing-data handling.
5. **Later:** live prices, authentication/ownership before public hosting, and a minimal dashboard.

Use small capability-focused commits with their tests. Add new migrations after a migration has been applied; do not rewrite migration history. No performance claims are made without measurements.
